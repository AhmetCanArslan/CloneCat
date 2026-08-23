package com.arslan.clonecat.device

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.arslan.clonecat.cmd.AdbCommandBuilder
import com.arslan.clonecat.shell.ShellResult
import kotlinx.coroutines.Dispatchers
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
    private val LIBRARY_FLAGS = setOf("STATIC_SHARED_LIBRARY", "SDK_LIBRARY")

    private val labelCache = LruCache<String, String>(512)
    private val iconCache = LruCache<String, Drawable>(128)

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
        if (apps.isNotEmpty()) return apps.sortedBy { it.packageName }

        val dump = Device.run(AdbCommandBuilder.dumpsysPackages())
        if (!dump.success) return emptyList()
        return parseDumpsysPackages(dump.stdout, setOf(userId))[userId]
            .orEmpty()
            .sortedBy { it.packageName }
    }

    internal fun parseDumpsysPackages(
        output: String,
        userIds: Set<Int>
    ): Map<Int, List<AppEntry>> {
        val byUser = mutableMapOf<Int, MutableList<AppEntry>>()
        val seen = mutableSetOf<String>()
        var pkg: String? = null
        var appId = -1
        var skip = false
        var isSystem = false
        var pendingUser = -1

        output.lineSequence().forEach { raw ->
            val token = raw.trim()

            if (DUMPSYS_END_TOKEN.matches(token)) {
                pkg = null
                return@forEach
            }
            DUMPSYS_PACKAGE_TOKEN.matchEntire(token)?.let { match ->
                val name = match.groupValues[1]
                pkg = name
                appId = -1
                isSystem = false
                pendingUser = -1
                skip = !seen.add(name)
                return@forEach
            }
            val current = pkg ?: return@forEach
            if (skip) return@forEach

            DUMPSYS_APP_ID_TOKEN.matchEntire(token)?.let { match ->
                if (appId < 0) appId = match.groupValues[1].toIntOrNull() ?: -1
                pendingUser = -1
                return@forEach
            }
            DUMPSYS_FLAGS_TOKEN.matchEntire(token)?.let { match ->
                isSystem = match.groupValues[1].split(" ").contains("SYSTEM")
                pendingUser = -1
                return@forEach
            }
            DUMPSYS_PRIVATE_FLAGS_TOKEN.matchEntire(token)?.let { match ->
                val flags = match.groupValues[1].split(" ")
                if (flags.any { flag -> LIBRARY_FLAGS.any { flag.endsWith(it) } }) skip = true
                pendingUser = -1
                return@forEach
            }
            DUMPSYS_USER_TOKEN.matchEntire(token)?.let { match ->
                pendingUser = match.groupValues[1].toIntOrNull() ?: -1
                return@forEach
            }
            DUMPSYS_INSTALLED_TOKEN.matchEntire(token)?.let { match ->
                val userId = pendingUser
                pendingUser = -1
                if (userId < 0 || appId < 0) return@forEach
                if (match.groupValues[1] != "true") return@forEach
                if (userId !in userIds) return@forEach
                val uid = userId * PER_USER_RANGE + appId % PER_USER_RANGE
                byUser.getOrPut(userId) { mutableListOf() }
                    .add(AppEntry(userId, current, uid, isSystem))
            }
        }
        return byUser
    }

    suspend fun install(userId: Int, pkg: String): ShellResult =
        Device.run(AdbCommandBuilder.installExisting(userId, pkg))

    suspend fun uninstall(userId: Int, pkg: String): ShellResult =
        Device.run(AdbCommandBuilder.uninstall(userId, pkg))

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

    suspend fun label(context: Context, userId: Int, pkg: String): String {
        labelCache.get(pkg)?.let { return it }
        val info = archiveInfo(context, userId, pkg)
        val label = info?.applicationInfo?.loadLabel(context.packageManager)?.toString()
            ?: userZeroLabel(context, pkg)
            ?: pkg
        labelCache.put(pkg, label)
        return label
    }

    suspend fun icon(context: Context, userId: Int, pkg: String): Drawable? {
        iconCache.get(pkg)?.let { return it }
        val fromArchive = archiveInfo(context, userId, pkg)
            ?.applicationInfo
            ?.loadIcon(context.packageManager)
        val icon = fromArchive ?: userZeroIcon(context, pkg)
        if (icon != null) iconCache.put(pkg, icon)
        return icon
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
