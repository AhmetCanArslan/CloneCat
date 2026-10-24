package com.arslan.clonecat.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.arslan.clonecat.R
import com.arslan.clonecat.adapters.PickApp
import com.arslan.clonecat.adapters.PickAppAdapter
import com.arslan.clonecat.databinding.ActivityClonePickerBinding
import com.arslan.clonecat.device.AppRepository
import com.arslan.clonecat.device.DeviceUser
import com.arslan.clonecat.device.FreezeRepository
import com.arslan.clonecat.shortcut.ShortcutRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

class FreezeListActivity : BaseActivity() {

    companion object {
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_USER_NAME = "user_name"
        const val EXTRA_USER_TYPE = "user_type"
    }

    private lateinit var binding: ActivityClonePickerBinding
    private lateinit var adapter: PickAppAdapter
    private lateinit var user: DeviceUser

    private val selected = linkedSetOf<String>()
    private var frozen: Set<String> = emptySet()
    private var apps: List<PickApp> = emptyList()
    private var systemFlags: Map<String, Boolean> = emptyMap()
    private var showSystem = false
    private var query = ""

    private val exportPicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> if (uri != null) exportList(uri) }

    private val importPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importList(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClonePickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        user = DeviceUser(
            id = intent.getIntExtra(EXTRA_USER_ID, 0),
            name = intent.getStringExtra(EXTRA_USER_NAME).orEmpty(),
            type = ShortcutRepository.typeOf(intent.getStringExtra(EXTRA_USER_TYPE)),
            running = true,
            unlocked = false
        )
        title = getString(R.string.action_freeze_apps)
        selected.addAll(FreezeRepository.list(this, user.id))

        adapter = PickAppAdapter(
            selected,
            bindIcon = { pkg, apply ->
                lifecycleScope.launch { apply(AppRepository.icon(this@FreezeListActivity, user.id, pkg)) }
            },
            onToggle = {
                FreezeRepository.save(this, user.id, selected.toSet())
                updateFab()
            },
            note = { pkg -> if (pkg in frozen) getString(R.string.frozen) else null }
        )
        binding.appList.layoutManager = LinearLayoutManager(this)
        binding.appList.adapter = adapter

        binding.searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim().orEmpty()
                render()
            }
        })
        showSystem = UiPrefs.showSystem(this)
        binding.systemChip.isChecked = showSystem
        binding.systemChip.setOnCheckedChangeListener { _, checked ->
            showSystem = checked
            UiPrefs.setShowSystem(this, checked)
            render()
        }
        binding.cloneFab.setOnClickListener { toggle() }

        load()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.freeze, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        R.id.action_pin_freeze -> {
            pin()
            true
        }
        R.id.action_freeze_export -> {
            exportPicker.launch("clonecat-freeze-${user.type.name.lowercase()}.txt")
            true
        }
        R.id.action_freeze_import -> {
            importPicker.launch(arrayOf("text/plain", "*/*"))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun load() {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            frozen = FreezeRepository.frozen(user.id)
            val entries = AppRepository.appsFor(user.id)
            systemFlags = entries.associate { it.packageName to it.isSystem }
            val packages = (entries.map { it.packageName } + frozen).distinct()
            apps = packages.map { pkg ->
                async { PickApp(pkg, AppRepository.label(this@FreezeListActivity, user.id, pkg)) }
            }.awaitAll().sortedWith(compareBy({ it.packageName !in selected && it.packageName !in frozen }, { it.label.lowercase() }))
            binding.progress.visibility = View.GONE
            render()
        }
    }

    private fun render() {
        val filtered = apps
            .filter { showSystem || systemFlags[it.packageName] != true || it.packageName in frozen }
            .filter {
                query.isEmpty() ||
                    it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        adapter.submit(filtered)
        binding.emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        updateFab()
    }

    private fun updateFab() {
        val thaw = frozen.containsAll(selected)
        binding.cloneFab.text = getString(if (thaw) R.string.unfreeze_count else R.string.freeze_count, selected.size)
        binding.cloneFab.setIconResource(if (thaw) R.drawable.ic_picon_light_mode else R.drawable.ic_frozen_claw)
        binding.cloneFab.visibility = if (selected.isEmpty()) View.GONE else View.VISIBLE
        val count = selected.count { it in frozen }
        supportActionBar?.subtitle = getString(R.string.freeze_subtitle, user.label(this), count, selected.size)
    }

    private fun toggle() {
        val freeze = !frozen.containsAll(selected)
        val targets = if (freeze) selected - frozen else selected.toSet()
        binding.progress.visibility = View.VISIBLE
        binding.cloneFab.isEnabled = false
        lifecycleScope.launch {
            val failed = FreezeRepository.apply(user.id, targets, freeze)
            frozen = FreezeRepository.frozen(user.id)
            binding.progress.visibility = View.GONE
            binding.cloneFab.isEnabled = true
            render()
            if (failed.isEmpty()) {
                toast(getString(if (freeze) R.string.freeze_done else R.string.unfreeze_done, targets.size))
                return@launch
            }
            MaterialAlertDialogBuilder(this@FreezeListActivity)
                .setTitle(R.string.freeze_failed_title)
                .setMessage(failed.joinToString("\n"))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun exportList(uri: android.net.Uri) {
        val ok = runCatching {
            contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(selected.sorted().joinToString("\n").toByteArray())
            } ?: error("no stream")
        }.isSuccess
        toast(if (ok) getString(R.string.freeze_exported, selected.size) else getString(R.string.export_failed))
    }

    private fun importList(uri: android.net.Uri) {
        val packages = runCatching {
            contentResolver.openInputStream(uri)?.use { FreezeRepository.parse(it.readBytes().decodeToString()) }
        }.getOrNull()
        if (packages.isNullOrEmpty()) {
            toast(getString(R.string.import_failed))
            return
        }
        val known = apps.map { it.packageName }.toSet()
        val usable = packages.filter { it in known }
        if (usable.size < packages.size) toast(getString(R.string.freeze_import_skipped, packages.size - usable.size))
        selected.addAll(usable)
        FreezeRepository.save(this, user.id, selected.toSet())
        render()
    }

    private fun pin() {
        if (selected.isEmpty()) {
            toast(getString(R.string.freeze_list_empty))
            return
        }
        lifecycleScope.launch {
            val ok = ShortcutRepository.pinFreeze(this@FreezeListActivity, user)
            toast(getString(if (ok) R.string.pin_requested else R.string.pin_unsupported))
        }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
