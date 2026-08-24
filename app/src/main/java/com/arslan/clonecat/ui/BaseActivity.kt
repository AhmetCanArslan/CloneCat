package com.arslan.clonecat.ui

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.arslan.clonecat.R
import com.arslan.clonecat.adapters.IconPickAdapter
import com.arslan.clonecat.databinding.DialogIconPickerBinding
import com.arslan.clonecat.databinding.DialogShortcutEditBinding
import com.arslan.clonecat.shortcut.ShortcutCustomization
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

open class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
    }

    fun editShortcut(id: String, defaultLabel: String, defaultIcon: Drawable?, onSave: () -> Unit) {
        val binding = DialogShortcutEditBinding.inflate(layoutInflater)
        binding.nameField.setText(ShortcutCustomization.name(this, id) ?: defaultLabel)

        var pickedIcon: Drawable? = null
        val custom = ShortcutCustomization.iconBitmap(this, id)
        if (custom != null) binding.iconPreview.setImageBitmap(custom)
        else binding.iconPreview.setImageDrawable(defaultIcon)

        binding.pickIcon.setOnClickListener {
            pickIcon { icon ->
                pickedIcon = icon
                binding.iconPreview.setImageDrawable(icon)
            }
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

    private fun pickIcon(onPicked: (Drawable) -> Unit) {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val icons = pm.queryIntentActivities(intent, 0)
            .map { it.loadLabel(pm).toString() to it.loadIcon(pm) }
            .sortedBy { it.first.lowercase() }

        val gridBinding = DialogIconPickerBinding.inflate(layoutInflater)
        gridBinding.iconGrid.layoutManager = GridLayoutManager(this, 4)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.shortcut_pick_icon)
            .setView(gridBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        gridBinding.iconGrid.adapter = IconPickAdapter(icons) { icon ->
            onPicked(icon)
            dialog.dismiss()
        }
    }
}
