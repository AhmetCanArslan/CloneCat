package com.arslan.clonecat.shortcut

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File

object ShortcutCustomization {

    private const val PREFS = "clonecat_shortcut_custom"

    fun name(context: Context, id: String): String? =
        prefs(context).getString(id, null)?.takeIf { it.isNotBlank() }

    fun setName(context: Context, id: String, value: String?) {
        prefs(context).edit().apply {
            if (value.isNullOrBlank()) remove(id) else putString(id, value.trim())
            apply()
        }
    }

    fun iconFile(context: Context, id: String): File? =
        File(dir(context), id.replace(Regex("[^A-Za-z0-9]"), "_") + ".png").takeIf { it.exists() }

    fun saveIcon(context: Context, id: String, uri: Uri): Boolean = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
        }
        val size = 512
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val file = File(dir(context), id.replace(Regex("[^A-Za-z0-9]"), "_") + ".png")
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        true
    }.getOrDefault(false)

    fun iconBitmap(context: Context, id: String): Bitmap? =
        iconFile(context, id)?.let { BitmapFactory.decodeFile(it.path) }

    fun clear(context: Context, id: String) {
        prefs(context).edit().remove(id).apply()
        iconFile(context, id)?.delete()
    }

    private fun dir(context: Context) =
        File(context.filesDir, "shortcut_icons").apply { mkdirs() }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
