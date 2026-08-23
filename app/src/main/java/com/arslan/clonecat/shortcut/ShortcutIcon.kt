package com.arslan.clonecat.shortcut

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import com.arslan.clonecat.R
import com.arslan.clonecat.device.UserType

object ShortcutIcon {

    fun colorFor(type: UserType): Int = when (type) {
        UserType.PRIMARY -> 0xFF3F51B5.toInt()
        UserType.SECONDARY -> 0xFF00897B.toInt()
        UserType.MANAGED -> 0xFF1565C0.toInt()
        UserType.CLONE -> 0xFFEF6C00.toInt()
        UserType.PRIVATE -> 0xFF6A1B9A.toInt()
        UserType.OTHER -> 0xFF546E7A.toInt()
    }

    fun of(context: Context, pkg: String, fallback: Drawable?): Icon {
        val drawable = fallback ?: appIcon(context, pkg)
        ?: return Icon.createWithResource(context, R.mipmap.ic_launcher)

        val size = iconSize(context)
        return if (drawable is AdaptiveIconDrawable) {
            Icon.createWithAdaptiveBitmap(fullBleed(drawable, size))
        } else {
            Icon.createWithBitmap(rasterize(drawable, size))
        }
    }

    private fun appIcon(context: Context, pkg: String): Drawable? = try {
        context.packageManager.getApplicationIcon(pkg)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun fullBleed(drawable: AdaptiveIconDrawable, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.background?.apply {
            setBounds(0, 0, size, size)
            draw(canvas)
        }
        drawable.foreground?.apply {
            setBounds(0, 0, size, size)
            draw(canvas)
        }
        return bitmap
    }

    private fun rasterize(drawable: Drawable, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }

    private fun iconSize(context: Context): Int {
        val manager = context.getSystemService(ActivityManager::class.java)
        val large = manager?.launcherLargeIconSize?.takeIf { it > 0 }
            ?: (108 * context.resources.displayMetrics.density).toInt()
        val max = context.getSystemService(ShortcutManager::class.java)?.iconMaxWidth ?: 0
        return if (max > 0) minOf(large, max) else large
    }
}
