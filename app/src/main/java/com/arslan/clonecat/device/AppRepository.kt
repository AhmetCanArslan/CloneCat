package com.arslan.clonecat.device

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.UserHandle
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.arslan.clonecat.cmd.AdbCommandBuilder
import com.arslan.clonecat.shell.ShellResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppEntry(
    val userId: Int,
    val packageName: String,
    val uid: Int,
    val isSystem: Boolean
)

object AppRepository {

    private const val PER_USER_RANGE = 100_000

    private val PACKAGE_LINE = Regex("""^package:(\S+)\s+uid:(\d+)$""")
    private val COMPONENT = Regex("""([A-Za-z0-9_.]+)/(\S+)""")
    private val DUMPSYS_PACKAGE_TOKEN = Regex("""^Package \[([^]]+)]$""")
    private val DUMPSYS_APP_ID_TOKEN = Regex("""^(?:appId|userId)=(\d+)$""")
    private val DUMPSYS_FLAGS_TOKEN = Regex("""^pkgFlags=\[(.*)]$""")
    private val DUMPSYS_PRIVATE_FLAGS_TOKEN = Regex("""^privateFlags=\[(.*)]$""")
    private val DUMPSYS_USER_TOKEN = Regex("""^User (\d+):$""")
    private val DUMPSYS_INSTALLED_TOKEN = Regex("""^installed=(\w+)$""")
    private val DUMPSYS_END_TOKEN = Regex("""^Shared users:$""")
    private val INSTALLER_LINE = Regex("""^package:(\S+)\s+installer=(\S+)$""")
    private val LIBRARY_FLAGS = setOf("STATIC_SHARED_LIBRARY", "SDK_LIBRARY")

    private val labelCache = LruCache<String, String>(512)
    private val iconCache = LruCache<String, Drawable>(512)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val labelJobs = mutableMapOf<String, Deferred<String>>()
    private val iconJobs = mutableMapOf<String, Deferred<Drawable?>>()

    private val appCache = mutableMapOf<Int, List<AppEntry>>()

    fun cachedApps(userId: Int): List<AppEntry>? = synchronized(appCache) { appCache[userId] }

    private fun cache(userId: Int, apps: List<AppEntry>): List<AppEntry> {
        if (apps.isNotEmpty()) synchronized(appCache) { appCache[userId] = apps }
        return apps
    }

    suspend fun appsFor(userId: Int): List<AppEntry> {
        val results = Device.runAll(
            listOf(
                AdbCommandBuilder.listThirdParty(userId),
                AdbCommandBuilder.listSystem(userId)
            )
        )

        val apps = mutableListOf<AppEntry>()
        results.forEachIndexed { index, result ->
            if (!result.success) return@forEachIndexed
            val isSystem = index == 1
            result.stdout.lineSequence().forEach inner@{ line ->
                val match = PACKAGE_LINE.matchEntire(line.trim()) ?: return@inner
                val uid = match.groupValues[2].toIntOrNull() ?: return@inner
                apps.add(AppEntry(userId, match.groupValues[1], uid, isSystem))
            }
        }
        if (apps.isNotEmpty()) return cache(userId, apps.sortedBy { it.packageName })

        val dump = Device.run(AdbCommandBuilder.dumpsysPackages())
        if (!dump.success) return emptyList()
        return cache(
            userId,
            parseDumpsysPackages(dump.stdout, setOf(userId))[userId]
                .orEmpty()
                .sortedBy { it.packageName }
        )
    }

    private class DumpsysScan(val userIds: Set<Int>) {
        val byUser = mutableMapOf<Int, MutableList<AppEntry>>()
        val seen = mutableSetOf<String>()
        var pkg: String? = null
        var appId = -1
        var skip = false
        var isSystem = false
        var pendingUser = -1

        fun startPackage(name: String) {
            pkg = name
            appId = -1
            isSystem = false
            pendingUser = -1
            skip = !seen.add(name)
        }

        fun add(userId: Int) {
            val name = pkg ?: return
            if (userId < 0 || appId < 0 || userId !in userIds) return
            val uid = userId * PER_USER_RANGE + appId % PER_USER_RANGE
            byUser.getOrPut(userId) { mutableListOf() }.add(AppEntry(userId, name, uid, isSystem))
        }
    }

    internal fun parseDumpsysPackages(
        output: String,
        userIds: Set<Int>
    ): Map<Int, List<AppEntry>> {
        val scan = DumpsysScan(userIds)
        output.lineSequence().forEach { scan.feed(it.trim()) }
        return scan.byUser
    }

    private fun DumpsysScan.feed(token: String) {
        if (DUMPSYS_END_TOKEN.matches(token)) {
            pkg = null
            return
        }
        DUMPSYS_PACKAGE_TOKEN.matchEntire(token)?.let { return startPackage(it.groupValues[1]) }
        if (pkg == null || skip) return

        DUMPSYS_USER_TOKEN.matchEntire(token)?.let {
            pendingUser = it.groupValues[1].toIntOrNull() ?: -1
            return
        }
        DUMPSYS_INSTALLED_TOKEN.matchEntire(token)?.let {
            val userId = pendingUser
            pendingUser = -1
            if (it.groupValues[1] == "true") add(userId)
            return
        }
        pendingUser = -1
        DUMPSYS_APP_ID_TOKEN.matchEntire(token)?.let {
            if (appId < 0) appId = it.groupValues[1].toIntOrNull() ?: -1
            return
        }
        DUMPSYS_FLAGS_TOKEN.matchEntire(token)?.let {
            isSystem = it.groupValues[1].split(" ").contains("SYSTEM")
            return
        }
        DUMPSYS_PRIVATE_FLAGS_TOKEN.matchEntire(token)?.let { match ->
            val flags = match.groupValues[1].split(" ")
            if (flags.any { flag -> LIBRARY_FLAGS.any { flag.endsWith(it) } }) skip = true
        }
    }

    fun fastApps(caller: Context, userId: Int): List<AppEntry> {
        val context = caller.applicationContext
        val handle = UserHandle.getUserHandleForUid(userId * PER_USER_RANGE)
        val activities = try {
            context.getSystemService(LauncherApps::class.java)
                ?.getActivityList(null, handle)
                .orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }

        return activities.map { info ->
            val pkg = info.applicationInfo.packageName
            val key = "$userId:$pkg"
            if (labelCache.get(key) == null) labelCache.put(key, info.label.toString())
            AppEntry(userId, pkg, info.applicationInfo.uid, isSystem = false)
        }.distinctBy { it.packageName }.sortedBy { it.packageName }
    }

    private const val PLAY = "com.android.vending"

    private val lostPlaySource = mutableSetOf<String>()

    fun takeLostPlaySource(): List<String> = synchronized(lostPlaySource) {
        val out = lostPlaySource.toList()
        lostPlaySource.clear()
        out
    }

    private val PLAY_STACK = listOf("com.android.vending", "com.google.android.gms", "com.google.android.gsf")

    suspend fun missingPlayStack(userId: Int): List<String> {
        if (userId == 0) return emptyList()
        val here = appsFor(userId).map { it.packageName }.toSet()
        val zero = appsFor(0).map { it.packageName }.toSet()
        return PLAY_STACK.filter { it in zero && it !in here }
    }

    suspend fun install(userId: Int, pkg: String, sources: List<Int> = listOf(0)): ShellResult {
        synchronized(appCache) { appCache.remove(userId) }
        val existing = Device.run(AdbCommandBuilder.installExisting(userId, pkg))
        if (existing.success && !existing.output.contains("Failure", ignoreCase = true)) return existing

        val hadPlay = installerOf(pkg) == PLAY
        val session = sessionInstall(userId, pkg, sources) ?: return existing
        if (hadPlay && session.success && installerOf(pkg) != PLAY) {
            synchronized(lostPlaySource) { lostPlaySource.add(pkg) }
        }
        return session
    }

    private suspend fun installerOf(pkg: String): String? {
        val out = Device.run(AdbCommandBuilder.installerOf(pkg))
        if (!out.success) return null
        return out.stdout.lineSequence()
            .mapNotNull { INSTALLER_LINE.matchEntire(it.trim()) }
            .firstOrNull { it.groupValues[1] == pkg }
            ?.groupValues?.get(2)
            ?.takeIf { it != "null" && it.isNotBlank() }
    }

    private suspend fun sessionInstall(userId: Int, pkg: String, sources: List<Int>): ShellResult? {
        val paths = (sources + 0).distinct().firstNotNullOfOrNull { source ->
            Device.run(AdbCommandBuilder.apkPath(source, pkg))
                .stdout
                .lineSequence()
                .filter { it.startsWith("package:") }
                .map { it.removePrefix("package:").trim() }
                .filter { it.endsWith(".apk") }
                .toList()
                .takeIf { it.isNotEmpty() }
        }.orEmpty()
        if (paths.isEmpty()) return null

        val create = Device.run(AdbCommandBuilder.installCreate(userId, pkg))
        val sessionId = Regex("\\[(\\d+)]").find(create.stdout)?.groupValues?.get(1) ?: return null

        paths.forEachIndexed { index, path ->
            val name = path.substringAfterLast('/').removeSuffix(".apk").ifBlank { "split$index" }
            val write = Device.run(AdbCommandBuilder.installWrite(sessionId, name, path))
            if (!write.success) {
                Device.run(AdbCommandBuilder.installAbandon(sessionId))
                return write
            }
        }

        return Device.run(AdbCommandBuilder.installCommit(sessionId))
    }

    suspend fun uninstall(userId: Int, pkg: String): ShellResult {
        synchronized(appCache) { appCache.remove(userId) }
        return Device.run(AdbCommandBuilder.uninstall(userId, pkg))
    }

    suspend fun launcherComponent(context: Context, userId: Int, pkg: String): String? {
        val query = Device.run(AdbCommandBuilder.queryLauncherActivities(userId, pkg))
        componentIn(query.stdout, pkg)?.let { return it }

        val resolve = Device.run(AdbCommandBuilder.resolveLauncherActivity(userId, pkg))
        componentIn(resolve.stdout, pkg)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                context.packageManager.getLaunchIntentForPackage(pkg)?.component?.flattenToShortString()
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun componentIn(output: String, pkg: String): String? = output.lineSequence()
        .mapNotNull { line -> COMPONENT.find(line.trim())?.value }
        .firstOrNull { it.substringBefore('/') == pkg }

    suspend fun label(caller: Context, userId: Int, pkg: String): String {
        val context = caller.applicationContext
        val key = "$userId:$pkg"
        labelCache.get(key)?.let { return it }
        val job = synchronized(labelJobs) {
            labelJobs.getOrPut(key) {
                scope.async {
                    val label = userZeroLabel(context, pkg)
                        ?: archiveInfo(context, userId, pkg)
                            ?.applicationInfo?.loadLabel(context.packageManager)?.toString()
                        ?: pkg
                    labelCache.put(key, label)
                    synchronized(labelJobs) { labelJobs.remove(key) }
                    label
                }
            }
        }
        return job.await()
    }

    suspend fun icon(caller: Context, userId: Int, pkg: String): Drawable? {
        val context = caller.applicationContext
        val key = "$userId:$pkg"
        iconCache.get(key)?.let { return it }
        val job = synchronized(iconJobs) {
            iconJobs.getOrPut(key) {
                scope.async {
                    val pm = context.packageManager
                    val icon = userZeroIcon(context, pkg)
                        ?: archiveInfo(context, userId, pkg)?.applicationInfo?.loadIcon(pm)
                        ?: launcherIcon(context, userId, pkg)
                        ?: pm.getDefaultActivityIcon()
                    iconCache.put(key, icon)
                    synchronized(iconJobs) { iconJobs.remove(key) }
                    icon
                }
            }
        }
        return job.await()
    }

    private var warmJob: Job? = null

    fun warm(caller: Context, userIds: List<Int>) {
        val context = caller.applicationContext
        if (warmJob?.isActive == true) return
        warmJob = scope.launch {
            userIds.forEach { userId ->
                val apps = appsFor(userId)
                apps.forEach { app ->
                    label(context, app.userId, app.packageName)
                    icon(context, app.userId, app.packageName)
                }
            }
        }
    }

    private var prefetchJob: Job? = null

    fun prefetch(context: Context, apps: List<AppEntry>) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            apps.forEach { app ->
                label(context, app.userId, app.packageName)
                icon(context, app.userId, app.packageName)
            }
        }
    }

    private fun launcherIcon(context: Context, userId: Int, pkg: String): Drawable? = try {
        val apps = context.getSystemService(LauncherApps::class.java)
            ?.getActivityList(pkg, UserHandle.getUserHandleForUid(userId * PER_USER_RANGE))
            .orEmpty()
        apps.firstOrNull()?.getBadgedIcon(0)
    } catch (_: Throwable) {
        null
    }

    private suspend fun archiveInfo(
        context: Context,
        userId: Int,
        pkg: String
    ) = withContext(Dispatchers.IO) {
        val path = Device.run(AdbCommandBuilder.apkPath(userId, pkg))
            .stdout
            .lineSequence()
            .firstOrNull { it.startsWith("package:") }
            ?.removePrefix("package:")
            ?.trim()
            ?: return@withContext null
        try {
            context.packageManager.getPackageArchiveInfo(path, 0)?.also { info ->
                info.applicationInfo?.apply {
                    sourceDir = path
                    publicSourceDir = path
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun userZeroLabel(context: Context, pkg: String): String? = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun userZeroIcon(context: Context, pkg: String): Drawable? = try {
        context.packageManager.getApplicationIcon(pkg)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
