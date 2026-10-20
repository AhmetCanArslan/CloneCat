package com.arslan.clonecat.device

import android.content.Context
import com.arslan.clonecat.R
import com.arslan.clonecat.shell.ShellResult

object DeviceErrors {

    fun explain(context: Context, result: ShellResult): String {
        val text = result.output.ifBlank { context.getString(R.string.error_exit_code, result.exitCode) }
        val lower = text.lowercase()
        return when {
            lower.contains("unable to install on cloned user") ->
                context.getString(R.string.error_clone_blocked)
            lower.contains("not running") || lower.contains("not unlocked") ->
                context.getString(R.string.error_user_stopped)
            lower.contains("do not have permission") || lower.contains("security exception") ->
                context.getString(R.string.error_no_permission)
            lower.contains("no such user") || lower.contains("bad user number") ->
                context.getString(R.string.error_no_user)
            lower.contains("unknown package") ->
                context.getString(R.string.error_unknown_package)
            else -> text
        }
    }
}
