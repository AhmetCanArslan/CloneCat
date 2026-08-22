package com.arslan.clonecat.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import com.arslan.clonecat.R
import com.arslan.clonecat.databinding.ActivitySetupBinding
import com.arslan.clonecat.shell.ShizukuGate
import rikka.shizuku.Shizuku

class SetupActivity : BaseActivity() {

    private lateinit var binding: ActivitySetupBinding

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        runCatching { Shizuku.addRequestPermissionResultListener(permissionListener) }

        binding.actionButton.setOnClickListener {
            when (ShizukuGate.state(this)) {
                ShizukuGate.State.NOT_INSTALLED -> openShizukuPage()
                ShizukuGate.State.NOT_RUNNING -> openShizukuApp()
                ShizukuGate.State.NO_PERMISSION -> ShizukuGate.requestPermission()
                ShizukuGate.State.READY -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onDestroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(permissionListener) }
        super.onDestroy()
    }

    private fun render() {
        val state = ShizukuGate.state(this)
        binding.stateText.setText(
            when (state) {
                ShizukuGate.State.NOT_INSTALLED -> R.string.setup_not_installed
                ShizukuGate.State.NOT_RUNNING -> R.string.setup_not_running
                ShizukuGate.State.NO_PERMISSION -> R.string.setup_no_permission
                ShizukuGate.State.READY -> R.string.setup_ready
            }
        )
        binding.actionButton.setText(
            when (state) {
                ShizukuGate.State.NOT_INSTALLED -> R.string.setup_action_install
                ShizukuGate.State.NOT_RUNNING -> R.string.setup_action_start
                ShizukuGate.State.NO_PERMISSION -> R.string.setup_action_grant
                ShizukuGate.State.READY -> R.string.setup_action_continue
            }
        )
    }

    private fun openShizukuApp() {
        val intent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (intent != null) startActivity(intent) else openShizukuPage()
    }

    private fun openShizukuPage() {
        val market = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=moe.shizuku.privileged.api")
        )
        val browser = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/"))
        val target = if (market.resolveActivity(packageManager) != null) market else browser
        runCatching { startActivity(target) }
    }
}
