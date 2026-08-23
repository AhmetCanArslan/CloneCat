package com.arslan.clonecat.shell

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku

object ShizukuGate {

    const val REQUEST_CODE = 4711
    private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"

    enum class State { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

    fun state(context: Context): State {
        val running = try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
        if (running) {
            return if (hasPermission()) State.READY else State.NO_PERMISSION
        }
        return if (isInstalled(context)) State.NOT_RUNNING else State.NOT_INSTALLED
    }

    fun isReady(context: Context): Boolean = state(context) == State.READY

    suspend fun awaitReady(context: Context, timeoutMs: Long = 3000): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            when (state(context)) {
                State.READY -> return true
                State.NOT_INSTALLED, State.NO_PERMISSION -> return false
                State.NOT_RUNNING -> if (SystemClock.uptimeMillis() >= deadline) return false
            }
            delay(25)
        }
    }

    fun hasPermission(): Boolean = try {
        if (Shizuku.isPreV11()) false
        else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun requestPermission() {
        try {
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (_: Throwable) {
        }
    }

    private fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
