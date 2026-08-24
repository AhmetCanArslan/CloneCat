package com.arslan.clonecat.ui

import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.arslan.clonecat.R
import com.arslan.clonecat.databinding.DialogShortcutEditBinding
import com.arslan.clonecat.shortcut.ShortcutCustomization
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

open class BaseActivity : AppCompatActivity() {

    private var onPicked: ((Uri) -> Unit)? = null

    private val iconPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked?.invoke(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
    }

    fun editShortcut(id: String, defaultLabel: String, defaultIcon: Drawable?, onSave: () -> Unit) {
        val binding = DialogShortcutEditBinding.inflate(layoutInflater)
        binding.nameField.setText(ShortcutCustomization.name(this, id) ?: defaultLabel)

        var pickedIcon: Uri? = null
        val custom = ShortcutCustomization.iconBitmap(this, id)
        if (custom != null) binding.iconPreview.setImageBitmap(custom)
        else binding.iconPreview.setImageDrawable(defaultIcon)

        binding.pickIcon.setOnClickListener {
            onPicked = { uri ->
                pickedIcon = uri
                binding.iconPreview.setImageURI(uri)
            }
            iconPicker.launch(
                PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    .build()
            )
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.shortcut_edit_title)
            .setView(binding.root)
            .setPositiveButton(R.string.pin) { _, _ ->
                val name = binding.nameField.text?.toString()?.trim().orEmpty()
                ShortcutCustomization.setName(this, id, if (name == defaultLabel) null else name)
                pickedIcon?.let { ShortcutCustomization.saveIcon(this, id, it) }
                onSave()
            }
            .setNeutralButton(R.string.shortcut_reset) { _, _ ->
                ShortcutCustomization.clear(this, id)
                onSave()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
