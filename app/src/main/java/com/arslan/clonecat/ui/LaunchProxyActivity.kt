package com.arslan.clonecat.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.arslan.clonecat.R
import com.arslan.clonecat.cmd.AdbCommandBuilder
import com.arslan.clonecat.device.AppRepository
import com.arslan.clonecat.device.Device
import com.arslan.clonecat.device.DeviceErrors
import com.arslan.clonecat.device.UserRepository
import com.arslan.clonecat.device.UserType
import com.arslan.clonecat.shell.ShellResult
import com.arslan.clonecat.shell.ShizukuGate
import com.arslan.clonecat.shortcut.ShortcutRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class LaunchProxyActivity : AppCompatActivity() {

    companion object {
        const val ACTION_LAUNCH = "com.arslan.clonecat.action.LAUNCH"
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_COMPONENT = "component"
        const val EXTRA_USER_TYPE = "user_type"
        const val EXTRA_USER_LABEL = "user_label"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        val userId = intent.getIntExtra(EXTRA_USER_ID, -1)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        if (userId < 0 || pkg.isNullOrBlank()) {
            done()
            return
        }
        val type = ShortcutRepository.typeOf(intent.getStringExtra(EXTRA_USER_TYPE))

        if (!ShizukuGate.isReady(this)) {
            toast(getString(R.string.proxy_shizuku_missing))
            startActivity(Intent(this, SetupActivity::class.java))
            done()
            return
        }

        lifecycleScope.launch { launchTarget(userId, pkg, type) }
    }

    private suspend fun launchTarget(userId: Int, pkg: String, type: UserType) {
        var component = intent.getStringExtra(EXTRA_COMPONENT)?.takeUnless { it.isBlank() }
        var result = component?.let { Device.run(AdbCommandBuilder.startActivity(userId, it)) }

        if (result == null || !started(result)) {
            val user = UserRepository.listUsers().firstOrNull { it.id == userId }
            if (user == null) {
                toast(getString(R.string.proxy_user_gone))
                done()
                return
            }
            if (!user.running) UserRepository.startUser(userId)

            component = AppRepository.launcherComponent(this, userId, pkg)
            if (component.isNullOrBlank()) {
                toast(getString(R.string.no_launcher_activity))
                done()
                return
            }
            result = Device.run(AdbCommandBuilder.startActivity(userId, component))
        }

        when {
            !started(result) -> {
                toast(DeviceErrors.explain(result))
                done()
            }
            type == UserType.SECONDARY -> offerSwitch(userId, component!!)
            else -> done()
        }
    }

    private fun started(result: ShellResult) =
        result.success && !result.output.contains("Error:", ignoreCase = true)

    private fun done() {
        finish()
        overridePendingTransition(0, 0)
    }

    private fun offerSwitch(userId: Int, component: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.switch_title)
            .setMessage(R.string.proxy_secondary_message)
            .setPositiveButton(R.string.switch_action) { _, _ ->
                lifecycleScope.launch {
                    UserRepository.switchUser(userId)
                    Device.run(AdbCommandBuilder.startActivity(userId, component))
                    done()
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> done() }
            .setOnCancelListener { done() }
            .show()
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
