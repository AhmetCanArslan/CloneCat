package com.arslan.clonecat.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.arslan.clonecat.R
import com.arslan.clonecat.adapters.GlyphPickAdapter
import com.arslan.clonecat.adapters.IconPickAdapter
import com.arslan.clonecat.databinding.DialogIconPickerBinding
import com.arslan.clonecat.databinding.DialogShortcutEditBinding
import com.arslan.clonecat.shortcut.IconGlyphs
import com.arslan.clonecat.shortcut.ShortcutCustomization
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout

open class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
    }

    fun editShortcut(id: String, defaultLabel: String, defaultIcon: Drawable?, onSave: () -> Unit) {
        val binding = DialogShortcutEditBinding.inflate(layoutInflater)
        binding.nameField.setText(ShortcutCustomization.name(this, id) ?: defaultLabel)

        var pickedDrawable: Drawable? = null
        var pickedBitmap: Bitmap? = null
        val custom = ShortcutCustomization.iconBitmap(this, id)
        if (custom != null) binding.iconPreview.setImageBitmap(custom)
        else binding.iconPreview.setImageDrawable(defaultIcon)

        binding.pickIcon.setOnClickListener {
            pickIcon(
                onAppIcon = { icon ->
                    pickedDrawable = icon
                    pickedBitmap = null
                    binding.iconPreview.setImageDrawable(icon)
                },
                onGlyph = { bitmap ->
                    pickedBitmap = bitmap
                    pickedDrawable = null
                    binding.iconPreview.setImageBitmap(bitmap)
                }
            )
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.shortcut_edit_title)
            .setView(binding.root)
            .setPositiveButton(R.string.pin) { _, _ ->
                val name = binding.nameField.text?.toString()?.trim().orEmpty()
                ShortcutCustomization.setName(this, id, if (name == defaultLabel) null else name)
                pickedDrawable?.let { ShortcutCustomization.saveIcon(this, id, it) }
                pickedBitmap?.let { ShortcutCustomization.saveBitmap(this, id, it) }
                onSave()
            }
            .setNeutralButton(R.string.shortcut_reset) { _, _ ->
                ShortcutCustomization.clear(this, id)
                onSave()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickIcon(onAppIcon: (Drawable) -> Unit, onGlyph: (Bitmap) -> Unit) {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val appIcons = pm.queryIntentActivities(intent, 0)
            .map { it.loadLabel(pm).toString() to it.loadIcon(pm) }
            .sortedBy { it.first.lowercase() }

        val gridBinding = DialogIconPickerBinding.inflate(layoutInflater)
        val tabs = gridBinding.iconTabs
        tabs.addTab(tabs.newTab().setText(R.string.icon_tab_apps))
        tabs.addTab(tabs.newTab().setText(R.string.icon_tab_custom))

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.shortcut_pick_icon)
            .setView(gridBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        val glyphColor = MaterialColors.getColor(gridBinding.root, androidx.appcompat.R.attr.colorPrimary)

        fun showApps() {
            gridBinding.iconGrid.layoutManager = GridLayoutManager(this, 4)
            gridBinding.iconGrid.adapter = IconPickAdapter(appIcons) { icon ->
                onAppIcon(icon)
                dialog.dismiss()
            }
        }

        fun showGlyphs() {
            gridBinding.iconGrid.layoutManager = GridLayoutManager(this, 5)
            gridBinding.iconGrid.adapter = GlyphPickAdapter(IconGlyphs.all) { res ->
                onGlyph(ShortcutCustomization.glyphBitmap(this, res, glyphColor))
                dialog.dismiss()
            }
        }

        showApps()
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (tab.position == 0) showApps() else showGlyphs()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }
}
