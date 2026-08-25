package com.arslan.clonecat.backup

import android.content.Context
import com.arslan.clonecat.device.AppRepository
import com.arslan.clonecat.device.DeviceErrors
import com.arslan.clonecat.device.DeviceUser
import com.arslan.clonecat.device.UserType
import com.arslan.clonecat.shortcut.ShortcutCustomization
import com.arslan.clonecat.shortcut.ShortcutRepository
import com.arslan.clonecat.device.UserRepository
import com.arslan.clonecat.shortcut.UserColors
import org.json.JSONArray
import org.json.JSONObject

data class BackupUser(
    val id: Int,
    val name: String,
    val type: UserType,
    val color: Int,
    val apps: List<String>
)

data class BackupShortcut(
    val id: String,
    val userId: Int,
    val pkg: String,
    val name: String?,
    val ring: Boolean,
    val component: String?
)

data class Backup(
    val users: List<BackupUser>,
    val shortcuts: List<BackupShortcut>
)

data class ImportResult(
    val installed: Int,
    val alreadyThere: Int,
    val notOnDevice: List<String>,
    val failures: List<String>
)

object BackupRepository {

    const val VERSION = 1

    suspend fun collect(context: Context, users: List<DeviceUser>): Backup {
        val backupUsers = users.map { user ->
            BackupUser(
                id = user.id,
                name = user.label,
                type = user.type,
                color = UserColors.of(context, user.id, user.type),
                apps = AppRepository.appsFor(user.id)
                    .filter { !it.isSystem }
                    .map { it.packageName }
            )
        }

        val exported = users.map { it.id }.toSet()
        val prefs = context.getSharedPreferences("clonecat_shortcuts", Context.MODE_PRIVATE)
        val shortcuts = ShortcutRepository.ids(context).mapNotNull { id ->
            if (!id.startsWith("u")) return@mapNotNull null
            val userId = id.removePrefix("u").substringBefore(':').toIntOrNull() ?: return@mapNotNull null
            if (userId !in exported) return@mapNotNull null
            val pkg = id.substringAfter(':', "")
            if (pkg.isEmpty()) return@mapNotNull null
            BackupShortcut(
                id = id,
                userId = userId,
                pkg = pkg,
                name = ShortcutCustomization.name(context, id),
                ring = ShortcutCustomization.ring(context, id),
                component = prefs.getString("comp:$id", null)
            )
        }

        return Backup(backupUsers, shortcuts)
    }

    fun toJson(backup: Backup): String {
        val users = JSONArray()
        backup.users.forEach { user ->
            users.put(
                JSONObject()
                    .put("id", user.id)
                    .put("name", user.name)
                    .put("type", user.type.name)
                    .put("color", user.color)
                    .put("apps", JSONArray(user.apps))
            )
        }
        val shortcuts = JSONArray()
        backup.shortcuts.forEach { shortcut ->
            shortcuts.put(
                JSONObject()
                    .put("userId", shortcut.userId)
                    .put("package", shortcut.pkg)
                    .put("name", shortcut.name ?: JSONObject.NULL)
                    .put("ring", shortcut.ring)
                    .put("component", shortcut.component ?: JSONObject.NULL)
            )
        }
        return JSONObject()
            .put("version", VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("device", android.os.Build.MODEL)
            .put("users", users)
            .put("shortcuts", shortcuts)
            .toString(2)
    }

    fun parse(raw: String): Backup {
        val root = JSONObject(raw)
        val users = mutableListOf<BackupUser>()
        val userArray = root.optJSONArray("users") ?: JSONArray()
        for (index in 0 until userArray.length()) {
            val item = userArray.getJSONObject(index)
            val apps = mutableListOf<String>()
            val appArray = item.optJSONArray("apps") ?: JSONArray()
            for (appIndex in 0 until appArray.length()) apps.add(appArray.getString(appIndex))
            users.add(
                BackupUser(
                    id = item.optInt("id", -1),
                    name = item.optString("name"),
                    type = runCatching { UserType.valueOf(item.optString("type")) }
                        .getOrDefault(UserType.OTHER),
                    color = item.optInt("color", 0),
                    apps = apps
                )
            )
        }

        val shortcuts = mutableListOf<BackupShortcut>()
        val shortcutArray = root.optJSONArray("shortcuts") ?: JSONArray()
        for (index in 0 until shortcutArray.length()) {
            val item = shortcutArray.getJSONObject(index)
            val userId = item.optInt("userId", -1)
            val pkg = item.optString("package")
            if (userId < 0 || pkg.isEmpty()) continue
            shortcuts.add(
                BackupShortcut(
                    id = ShortcutRepository.idFor(userId, pkg),
                    userId = userId,
                    pkg = pkg,
                    name = item.optString("name").takeIf { it.isNotBlank() && it != "null" },
                    ring = item.optBoolean("ring", true),
                    component = item.optString("component").takeIf { it.isNotBlank() && it != "null" }
                )
            )
        }

        return Backup(users, shortcuts)
    }

    suspend fun restoreInto(
        context: Context,
        backup: Backup,
        saved: BackupUser,
        target: DeviceUser,
        users: List<DeviceUser>
    ): ImportResult {
        val installedBy = mutableMapOf<String, MutableList<Int>>()
        users.forEach { user ->
            AppRepository.appsFor(user.id).forEach { app ->
                installedBy.getOrPut(app.packageName) { mutableListOf() }.add(user.id)
            }
        }

        if (!target.running) UserRepository.startUser(target.id)

        var installed = 0
        var alreadyThere = 0
        val notOnDevice = linkedSetOf<String>()
        val failures = mutableListOf<String>()

        saved.apps.forEach { pkg ->
            val sources = installedBy[pkg].orEmpty()
            when {
                target.id in sources -> alreadyThere++
                sources.isEmpty() -> notOnDevice.add(pkg)
                else -> {
                    val result = AppRepository.install(target.id, pkg, sources)
                    if (result.success) installed++
                    else failures.add("$pkg: ${DeviceErrors.explain(result)}")
                }
            }
        }

        val prefs = context.getSharedPreferences("clonecat_shortcuts", Context.MODE_PRIVATE)
        backup.shortcuts.filter { it.userId == saved.id }.forEach { shortcut ->
            val id = ShortcutRepository.idFor(target.id, shortcut.pkg)
            shortcut.name?.let { ShortcutCustomization.setName(context, id, it) }
            ShortcutCustomization.setRing(context, id, shortcut.ring)
            shortcut.component?.let { prefs.edit().putString("comp:$id", it).apply() }
        }

        return ImportResult(installed, alreadyThere, notOnDevice.toList(), failures)
    }
}
