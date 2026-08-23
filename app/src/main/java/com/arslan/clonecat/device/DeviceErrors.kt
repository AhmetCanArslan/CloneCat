package com.arslan.clonecat.device

import com.arslan.clonecat.shell.ShellResult

object DeviceErrors {

    fun explain(result: ShellResult): String {
        val text = result.output.ifBlank { "exit code ${result.exitCode}" }
        val lower = text.lowercase()
        return when {
            lower.contains("unable to install on cloned user") ->
                "This ROM refuses `install-existing` for third-party apps in a clone user. " +
                    "Use the OEM Dual Apps screen for this package."
            lower.contains("not running") || lower.contains("not unlocked") ->
                "Target user is stopped or locked. Start it (and unlock private space) first."
            lower.contains("do not have permission") || lower.contains("security exception") ->
                "Shizuku's shell lacks permission for this user. Some OEM profiles block it."
            lower.contains("no such user") || lower.contains("bad user number") ->
                "That user no longer exists. Refresh the user list."
            lower.contains("unknown package") ->
                "The package is not installed for user 0, so there is nothing to clone."
            else -> text
        }
    }
}
