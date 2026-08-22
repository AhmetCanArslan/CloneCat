package com.arslan.clonecat.shortcut

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import com.arslan.clonecat.device.UserType
import kotlin.math.min

/** Draws the app icon with a small colored badge marking which user the shortcut targets. */
object IconBadger {

    private const val SIZE_DP = 108

    fun colorFor(type: UserType): Int = when (type) {
        UserType.PRIMARY -> 0xFF3F51B5.toInt()
        UserType.SECONDARY -> 0xFF00897B.toInt()
        UserType.MANAGED -> 0xFF1565C0.toInt()
        UserType.CLONE -> 0xFFEF6C00.toInt()
        UserType.PRIVATE -> 0xFF6A1B9A.toInt()
        UserType.OTHER -> 0xFF546E7A.toInt()
    }

    fun badged(context: Context, icon: Drawable?, type: UserType, userId: Int): Bitmap {
        val size = (SIZE_DP * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        icon?.apply {
            setBounds(0, 0, size, size)
            draw(canvas)
        }

        val radius = size * 0.22f
        val cx = size - radius - size * 0.04f
        val cy = size - radius - size * 0.04f

        val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorFor(type) }
        canvas.drawCircle(cx, cy, radius, circle)

        val text = badgeText(type, userId)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = radius * if (text.length > 1) 0.9f else 1.2f
            isFakeBoldText = true
        }
        val baseline = cy - (label.descent() + label.ascent()) / 2f
        canvas.drawText(text, cx, baseline, label)
        return bitmap
    }

    private fun badgeText(type: UserType, userId: Int): String {
        val letter = when (type) {
            UserType.PRIMARY -> "P"
            UserType.SECONDARY -> "S"
            UserType.MANAGED -> "W"
            UserType.CLONE -> "C"
            UserType.PRIVATE -> "V"
            UserType.OTHER -> "U"
        }
        return letter + min(userId, 99)
    }
}
