package com.arslan.clonecat.shortcut

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.util.DisplayMetrics
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
        val drawable = hiResIcon(context, pkg) ?: fallback
            ?: return Icon.createWithResource(context, R.mipmap.ic_launcher)

        val size = iconSize(context)
        return if (drawable is AdaptiveIconDrawable) {
            Icon.createWithAdaptiveBitmap(fullBleed(drawable, size))
        } else {
            Icon.createWithBitmap(rasterize(drawable, size))
        }
    }

    fun folder(context: Context, type: UserType): Icon {
        val size = iconSize(context)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(colorFor(type))

        val glyph = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_folder)
            ?.apply { setTint(0xFFFFFFFF.toInt()) } ?: return Icon.createWithBitmap(bitmap)
        val inset = (size * 0.3f).toInt()
        glyph.setBounds(inset, inset, size - inset, size - inset)
        glyph.draw(canvas)

        return Icon.createWithAdaptiveBitmap(bitmap)
    }

    private fun hiResIcon(context: Context, pkg: String): Drawable? = try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(pkg, 0)
        val res = pm.getResourcesForApplication(info)
        val id = info.icon
        if (id == 0) pm.getApplicationIcon(info)
        else res.getDrawableForDensity(id, DisplayMetrics.DENSITY_XXXHIGH, null)
            ?: pm.getApplicationIcon(info)
    } catch (_: Throwable) {
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
        (drawable as? BitmapDrawable)?.paint?.apply {
            isFilterBitmap = true
            isAntiAlias = true
        }
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }

    private fun iconSize(context: Context): Int {
        val manager = context.getSystemService(ActivityManager::class.java)
        val large = manager?.launcherLargeIconSize?.takeIf { it > 0 }
            ?: (108 * context.resources.displayMetrics.density).toInt()
        val max = context.getSystemService(ShortcutManager::class.java)?.iconMaxWidth ?: 0
        return maxOf(large * 2, max)
    }
}
