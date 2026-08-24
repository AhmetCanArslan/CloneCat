package com.arslan.clonecat.shortcut

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.util.DisplayMetrics
import com.arslan.clonecat.R
import com.arslan.clonecat.device.DeviceUser
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

    fun of(context: Context, pkg: String, fallback: Drawable?, user: DeviceUser? = null): Icon {
        val drawable = hiResIcon(context, pkg) ?: fallback
            ?: return Icon.createWithResource(context, R.mipmap.ic_launcher)

        val size = iconSize(context)
        val bitmap = if (drawable is AdaptiveIconDrawable) fullBleed(drawable, size)
        else rasterize(drawable, size)
        if (user != null && user.id != 0) drawRing(bitmap, UserColors.of(context, user.id, user.type))
        return Icon.createWithAdaptiveBitmap(bitmap)
    }

    fun custom(context: Context, id: String): Icon? =
        ShortcutCustomization.iconBitmap(context, id)?.let { Icon.createWithAdaptiveBitmap(it) }

    fun userScreen(context: Context, user: DeviceUser): Icon {
        val size = iconSize(context)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(UserColors.of(context, user.id, user.type))

        val glyph = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_apps_grid)
            ?.apply { setTint(0xFFFFFFFF.toInt()) } ?: return Icon.createWithBitmap(bitmap)
        val inset = (size * 0.3f).toInt()
        glyph.setBounds(inset, inset, size - inset, size - inset)
        glyph.draw(canvas)

        return Icon.createWithAdaptiveBitmap(bitmap)
    }

    private fun maskPath(side: Float): Path {
        val mask = runCatching {
            AdaptiveIconDrawable(null, null)
                .apply { setBounds(0, 0, side.toInt(), side.toInt()) }
                .iconMask
        }.getOrNull()
        if (mask == null || mask.isEmpty) {
            return Path().apply { addCircle(side / 2f, side / 2f, side / 2f, Path.Direction.CW) }
        }
        return Path(mask)
    }

    private fun drawRing(bitmap: Bitmap, color: Int) {
        val size = bitmap.width.toFloat()
        val stroke = size * 0.07f
        val side = size * 2f / 3f
        val path = maskPath(side)
        path.transform(Matrix().apply { setTranslate(size / 6f, size / 6f) })

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = color
        Canvas(bitmap).drawPath(path, paint)
    }

    fun preview(drawable: Drawable, color: Int?): Bitmap {
        val size = 256
        val base = if (drawable is AdaptiveIconDrawable) fullBleed(drawable, size)
        else rasterize(drawable, size)
        if (color != null) drawRing(base, color)

        val inset = size / 6
        val cropped = Bitmap.createBitmap(base, inset, inset, size - inset * 2, size - inset * 2)
        val out = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xFF000000.toInt()
        canvas.drawPath(maskPath(cropped.width.toFloat()), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(cropped, 0f, 0f, paint)
        return out
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
        drawable.background?.let { layer ->
            val saved = Rect(layer.bounds)
            layer.setBounds(0, 0, size, size)
            layer.draw(canvas)
            layer.bounds = saved
        }
        drawable.foreground?.let { layer ->
            val saved = Rect(layer.bounds)
            layer.setBounds(0, 0, size, size)
            layer.draw(canvas)
            layer.bounds = saved
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
        val saved = Rect(drawable.bounds)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        drawable.bounds = saved
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
