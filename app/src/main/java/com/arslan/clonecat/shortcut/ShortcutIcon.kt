package com.arslan.clonecat.shortcut

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
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

    /**
     * The app's own launcher icon, unaltered.
     *
     * Preferred form is a resource reference: the launcher then loads and masks the icon exactly as
     * it does on the app's normal home-screen entry. Only when the package is invisible to user 0
     * do we fall back to a bitmap, and an adaptive icon must then be rendered **full-bleed** — its
     * two layers drawn across the whole canvas without the drawable's own mask, because
     * `createWithAdaptiveBitmap` masks and scales again. Handing over a pre-masked bitmap is what
     * made pinned icons look shifted and cropped.
     */
    fun of(context: Context, pkg: String, fallback: Drawable?): Icon {
        resourceIcon(context, pkg)?.let { return it }
        if (fallback == null) return Icon.createWithResource(context, R.mipmap.ic_launcher)

        val size = launcherIconSize(context)
        return if (fallback is AdaptiveIconDrawable) {
            Icon.createWithAdaptiveBitmap(fullBleed(fallback, size))
        } else {
            Icon.createWithBitmap(rasterize(fallback, size))
        }
    }

    private fun resourceIcon(context: Context, pkg: String): Icon? = try {
        val info = context.packageManager.getApplicationInfo(pkg, 0)
        val resId = if (info.icon != 0) info.icon else 0
        if (resId == 0) null else Icon.createWithResource(pkg, resId)
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

    private fun launcherIconSize(context: Context): Int {
        val manager = context.getSystemService(ActivityManager::class.java)
        return manager?.launcherLargeIconSize?.takeIf { it > 0 }
            ?: (108 * context.resources.displayMetrics.density).toInt()
    }
}
