package com.arslan.clonecat.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.widget.Toast
import com.arslan.clonecat.R
import com.arslan.clonecat.cmd.CommandLog
import com.arslan.clonecat.databinding.SheetCommandsBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

/** Shows the adb equivalents of everything the app has run, with copy-all. */
object CommandsSheet {

    fun show(context: Context) {
        val binding = SheetCommandsBinding.inflate(LayoutInflater.from(context))
        val entries = CommandLog.snapshot()

        binding.commandsText.text = if (entries.isEmpty()) {
            context.getString(R.string.commands_empty)
        } else {
            entries.joinToString("\n\n") { entry ->
                val status = if (entry.success) "✔" else "✖ (${entry.exitCode})"
                "$status ${entry.command.label}\n${entry.command.asAdb()}"
            }
        }

        val dialog = BottomSheetDialog(context)
        dialog.setContentView(binding.root)

        binding.copyButton.setOnClickListener {
            val script = CommandLog.asAdbScript()
            if (script.isBlank()) return@setOnClickListener
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("adb", script))
            Toast.makeText(context, R.string.commands_copied, Toast.LENGTH_SHORT).show()
        }
        binding.clearButton.setOnClickListener {
            CommandLog.clear()
            dialog.dismiss()
        }
        dialog.show()
    }
}
