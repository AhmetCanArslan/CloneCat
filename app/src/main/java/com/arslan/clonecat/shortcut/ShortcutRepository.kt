package com.arslan.clonecat.shortcut

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import com.arslan.clonecat.device.AppRepository
import com.arslan.clonecat.device.DeviceUser
import com.arslan.clonecat.device.UserType
import com.arslan.clonecat.ui.LaunchProxyActivity

object ShortcutRepository {

    private const val PREFS = "clonecat_shortcuts"
    private const val KEY_IDS = "pinned_ids"

    fun idFor(userId: Int, pkg: String) = "u$userId:$pkg"

    fun isSupported(context: Context): Boolean =
        context.getSystemService(ShortcutManager::class.java)?.isRequestPinShortcutSupported == true

    suspend fun pin(
        context: Context,
        user: DeviceUser,
        pkg: String,
        component: String?
    ): Boolean {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!manager.isRequestPinShortcutSupported) return false

        val shortcut = build(context, user, pkg, component)
        val requested = runCatching { manager.requestPinShortcut(shortcut, null) }.getOrDefault(false)
        if (requested) remember(context, shortcut.id)
        return requested
    }

    suspend fun build(
        context: Context,
        user: DeviceUser,
        pkg: String,
        component: String?
    ): ShortcutInfo {
        val id = idFor(user.id, pkg)
        val label = AppRepository.label(context, user.id, pkg)
        val icon = AppRepository.icon(context, user.id, pkg)
        val target = component ?: rememberedComponent(context, id)
        if (target != null) prefs(context).edit().putString("comp:$id", target).apply()

        val intent = Intent(context, LaunchProxyActivity::class.java).apply {
            action = LaunchProxyActivity.ACTION_LAUNCH
            `package` = context.packageName
            setClassName(context.packageName, LaunchProxyActivity::class.java.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            putExtra(LaunchProxyActivity.EXTRA_USER_ID, user.id)
            putExtra(LaunchProxyActivity.EXTRA_PACKAGE, pkg)
            putExtra(LaunchProxyActivity.EXTRA_COMPONENT, target)
            putExtra(LaunchProxyActivity.EXTRA_USER_TYPE, user.type.name)
            putExtra(LaunchProxyActivity.EXTRA_USER_LABEL, user.label)
        }

        return ShortcutInfo.Builder(context, id)
            .setShortLabel(label)
            .setLongLabel("$label · ${user.label}")
            .setIcon(ShortcutIcon.of(context, pkg, icon))
            .setIntent(intent)
            .build()
    }

    suspend fun sync(context: Context, users: List<DeviceUser>, appsByUser: Map<Int, Set<String>>) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val known = ids(context)
        if (known.isEmpty()) return

        val usersById = users.associateBy { it.id }
        val alive = mutableListOf<ShortcutInfo>()
        val stale = mutableListOf<String>()

        known.forEach { id ->
            val userId = id.removePrefix("u").substringBefore(':').toIntOrNull()
            val pkg = id.substringAfter(':', "")
            val user = userId?.let { usersById[it] }
            if (user == null || pkg.isEmpty() || appsByUser[user.id]?.contains(pkg) != true) {
                stale.add(id)
                return@forEach
            }
            alive.add(build(context, user, pkg, null))
        }

        if (alive.isNotEmpty()) runCatching { manager.updateShortcuts(alive) }
        if (stale.isNotEmpty()) {
            runCatching {
                manager.disableShortcuts(stale, context.getString(com.arslan.clonecat.R.string.shortcut_stale))
            }
            forget(context, stale)
        }
    }

    private fun rememberedComponent(context: Context, id: String): String? =
        prefs(context).getString("comp:$id", null)

    fun ids(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_IDS, emptySet()).orEmpty()

    private fun remember(context: Context, id: String) {
        val updated = ids(context).toMutableSet().apply { add(id) }
        prefs(context).edit().putStringSet(KEY_IDS, updated).apply()
    }

    private fun forget(context: Context, removed: Collection<String>) {
        val updated = ids(context).toMutableSet().apply { removeAll(removed.toSet()) }
        prefs(context).edit().apply {
            putStringSet(KEY_IDS, updated)
            removed.forEach { remove("comp:$it") }
            apply()
        }
    }

    fun typeOf(name: String?): UserType =
        runCatching { UserType.valueOf(name ?: "") }.getOrDefault(UserType.OTHER)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
