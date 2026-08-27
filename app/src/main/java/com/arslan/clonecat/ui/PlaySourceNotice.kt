package com.arslan.clonecat.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.arslan.clonecat.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun Activity.warnLostPlaySource(packages: List<String>) {
    if (packages.isEmpty()) return
    MaterialAlertDialogBuilder(this)
        .setTitle(R.string.play_source_title)
        .setMessage(getString(R.string.play_source_message, packages.joinToString("\n")))
        .setPositiveButton(R.string.play_source_action) { _, _ ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${packages.first()}"))
            try {
                startActivity(intent)
            } catch (_: Throwable) {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=${packages.first()}")
                    )
                )
            }
        }
        .setNegativeButton(android.R.string.ok, null)
        .show()
}
