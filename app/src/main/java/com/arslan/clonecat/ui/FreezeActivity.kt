package com.arslan.clonecat.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.arslan.clonecat.R
import com.arslan.clonecat.device.FreezeRepository
import com.arslan.clonecat.shell.ShizukuGate
import kotlinx.coroutines.launch

class FreezeActivity : ComponentActivity() {

    companion object {
        const val ACTION_TOGGLE = "com.arslan.clonecat.action.FREEZE"
        const val EXTRA_USER_ID = "user_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launch_proxy)
        lifecycleScope.launch { toggle(intent.getIntExtra(EXTRA_USER_ID, -1)) }
    }

    private suspend fun toggle(userId: Int) {
        if (!ShizukuGate.awaitReady(this)) {
            toast(getString(R.string.proxy_shizuku_missing))
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        val targets = FreezeRepository.list(this, userId)
        if (userId < 0 || targets.isEmpty()) {
            toast(getString(R.string.freeze_list_empty))
            finish()
            return
        }
        val freeze = !FreezeRepository.frozen(userId).containsAll(targets)
        val failed = FreezeRepository.apply(userId, targets, freeze)
        toast(getString(if (freeze) R.string.freeze_done else R.string.unfreeze_done, targets.size - failed.size))
        finish()
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
