package com.arslan.clonecat.shortcut

import android.content.Context
import com.arslan.clonecat.device.UserType

object UserColors {

    val palette = listOf(
        0xFFEF6C00.toInt(),
        0xFFD81B60.toInt(),
        0xFF6A1B9A.toInt(),
        0xFF1565C0.toInt(),
        0xFF00897B.toInt(),
        0xFF2E7D32.toInt(),
        0xFFF9A825.toInt(),
        0xFF546E7A.toInt()
    )

    fun of(context: Context, userId: Int, type: UserType): Int =
        prefs(context).getInt("color:$userId", ShortcutIcon.colorFor(type))

    fun set(context: Context, userId: Int, color: Int) {
        prefs(context).edit().putInt("color:$userId", color).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("clonecat_ui", Context.MODE_PRIVATE)
}
