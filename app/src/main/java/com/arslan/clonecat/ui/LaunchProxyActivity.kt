package com.arslan.clonecat.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.arslan.clonecat.R
import com.arslan.clonecat.cmd.AdbCommandBuilder
import com.arslan.clonecat.device.AppRepository
import com.arslan.clonecat.device.Device
import com.arslan.clonecat.device.DeviceErrors
import com.arslan.clonecat.device.DeviceUser
import com.arslan.clonecat.device.PrivateCredentialStore
import com.arslan.clonecat.device.UserRepository
import com.arslan.clonecat.device.UserType
import com.arslan.clonecat.shell.ShellResult
import com.arslan.clonecat.shell.ShizukuGate
import com.arslan.clonecat.shortcut.ShortcutRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class LaunchProxyActivity : ComponentActivity() {

    companion object {
        const val ACTION_LAUNCH = "com.arslan.clonecat.action.LAUNCH"
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_COMPONENT = "component"
        const val EXTRA_USER_TYPE = "user_type"
        const val EXTRA_USER_LABEL = "user_label"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launch_proxy)

        val userId = intent.getIntExtra(EXTRA_USER_ID, -1)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        if (userId < 0 || pkg.isNullOrBlank()) {
            finish()
            return
        }
        val type = ShortcutRepository.typeOf(intent.getStringExtra(EXTRA_USER_TYPE))

        lifecycleScope.launch { launchTarget(userId, pkg, type) }
    }

    private suspend fun launchTarget(userId: Int, pkg: String, type: UserType) {
        if (!ShizukuGate.awaitReady(this)) {
            toast(getString(R.string.proxy_shizuku_missing))
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        if (type == UserType.PRIVATE && !preparePrivate(userId)) return

        var component = intent.getStringExtra(EXTRA_COMPONENT)?.takeUnless { it.isBlank() }
        var result = component?.let { Device.run(AdbCommandBuilder.startActivity(userId, it)) }

        if (result == null || !started(result)) {
            component = resolveComponent(userId, pkg) ?: return
            result = Device.run(AdbCommandBuilder.startActivity(userId, component))
        }

        when {
            !started(result) -> {
                toast(DeviceErrors.explain(result))
                finish()
            }
            type == UserType.SECONDARY -> offerSwitch(userId, component!!)
            else -> finish()
        }
    }

    private suspend fun preparePrivate(userId: Int): Boolean {
        var user: DeviceUser? = userOrFinish(userId) ?: return false
        if (user?.running != true) {
            UserRepository.startUser(userId)
            user = UserRepository.listUsers().firstOrNull { it.id == userId }
        }
        val pin = PrivateCredentialStore.get(this)
        if (!pin.isNullOrBlank()) {
            Device.run(AdbCommandBuilder.unlockUser(userId, pin))
            return true
        }
        if (user != null && !user.unlocked) {
            toast(getString(R.string.proxy_private_locked))
            finish()
            return false
        }
        return true
    }

    private suspend fun resolveComponent(userId: Int, pkg: String): String? {
        val user = userOrFinish(userId) ?: return null
        if (!user.running) UserRepository.startUser(userId)

        val component = AppRepository.launcherComponent(this, userId, pkg)
        if (!component.isNullOrBlank()) return component
        toast(getString(R.string.no_launcher_activity))
        finish()
        return null
    }

    private suspend fun userOrFinish(userId: Int): DeviceUser? {
        val user = UserRepository.listUsers().firstOrNull { it.id == userId }
        if (user == null) {
            toast(getString(R.string.proxy_user_gone))
            finish()
        }
        return user
    }

    private fun started(result: ShellResult) =
        result.success && !result.output.contains("Error:", ignoreCase = true)

    private fun offerSwitch(userId: Int, component: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.switch_title)
            .setMessage(R.string.proxy_secondary_message)
            .setPositiveButton(R.string.switch_action) { _, _ ->
                lifecycleScope.launch {
                    UserRepository.switchUser(userId)
                    Device.run(AdbCommandBuilder.startActivity(userId, component))
                    finish()
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
