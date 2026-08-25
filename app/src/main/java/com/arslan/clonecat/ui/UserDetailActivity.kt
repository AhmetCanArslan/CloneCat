package com.arslan.clonecat.ui

import android.content.Intent
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
import com.arslan.clonecat.adapters.AppAdapter
import com.arslan.clonecat.databinding.ActivityUserDetailBinding
import com.arslan.clonecat.device.AppEntry
import com.arslan.clonecat.device.AppRepository
import com.arslan.clonecat.device.DeviceErrors
import com.arslan.clonecat.device.DeviceUser
import com.arslan.clonecat.device.PrivateCredentialStore
import com.arslan.clonecat.device.UserRepository
import com.arslan.clonecat.device.UserType
import com.arslan.clonecat.shortcut.ShortcutRepository
import com.arslan.clonecat.shortcut.UserColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class UserDetailActivity : BaseActivity() {

    companion object {
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_USER_NAME = "user_name"
        const val EXTRA_USER_TYPE = "user_type"
        const val EXTRA_USER_RUNNING = "user_running"
    }

    private lateinit var binding: ActivityUserDetailBinding
    private lateinit var adapter: AppAdapter
    private lateinit var user: DeviceUser

    private var allApps: List<AppEntry> = emptyList()
    private var showSystem = false
    private var query: String = ""

    private val exportPicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val backup = com.arslan.clonecat.backup.BackupRepository.collect(
                this@UserDetailActivity,
                listOf(user)
            )
            val ok = runCatching {
                contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(com.arslan.clonecat.backup.BackupRepository.toJson(backup).toByteArray())
                } ?: error("no stream")
            }.isSuccess
            toast(
                if (ok) getString(R.string.export_user_done, backup.users.firstOrNull()?.apps?.size ?: 0)
                else getString(R.string.export_failed)
            )
        }
    }

    private val importPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) readBackup(uri) }

    private val clonePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val packages = result.data?.getStringArrayExtra(ClonePickerActivity.RESULT_PACKAGES).orEmpty()
        if (result.resultCode == RESULT_OK && packages.isNotEmpty()) cloneInto(packages.toList())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        user = DeviceUser(
            id = intent.getIntExtra(EXTRA_USER_ID, 0),
            name = intent.getStringExtra(EXTRA_USER_NAME).orEmpty(),
            type = ShortcutRepository.typeOf(intent.getStringExtra(EXTRA_USER_TYPE)),
            running = intent.getBooleanExtra(EXTRA_USER_RUNNING, false),
            unlocked = false
        )
        title = user.label

        adapter = AppAdapter(
            bindAsync = { app, apply ->
                lifecycleScope.launch {
                    apply(
                        AppAdapter.AppMetadata(
                            AppRepository.label(this@UserDetailActivity, app.userId, app.packageName),
                            AppRepository.icon(this@UserDetailActivity, app.userId, app.packageName)
                        )
                    )
                }
            },
            onLaunch = { app -> launch(app) },
            onPin = { app -> pin(app) },
            onRemove = { app -> confirmRemove(app) }
        )
        binding.appList.layoutManager = LinearLayoutManager(this)
        binding.appList.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { load() }

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
        binding.cloneFab.setOnClickListener { openClonePicker() }

        load()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.user_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        R.id.action_pin_all -> {
            pinAll()
            true
        }
        R.id.action_export_user -> {
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
                .format(java.util.Date())
            exportPicker.launch("clonecat-${user.type.name.lowercase()}-$stamp.json")
            true
        }
        R.id.action_import_user -> {
            importPicker.launch(arrayOf("application/json", "text/plain", "*/*"))
            true
        }
        R.id.action_start_user -> {
            runUserAction { UserRepository.startUser(user.id) }
            true
        }
        R.id.action_stop_user -> {
            runUserAction { UserRepository.stopUser(user.id) }
            true
        }
        R.id.action_user_color -> {
            pickUserColor()
            true
        }
        R.id.action_switch_user -> {
            confirmSwitch()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun load() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            allApps = AppRepository.appsFor(user.id)
            binding.swipeRefresh.isRefreshing = false
            render()
        }
    }

    private fun readBackup(uri: android.net.Uri) {
        val backup = runCatching {
            val raw = contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: error("no stream")
            com.arslan.clonecat.backup.BackupRepository.parse(raw)
        }.getOrNull()
        if (backup == null || backup.users.isEmpty()) {
            toast(getString(R.string.import_failed))
            return
        }
        if (backup.users.size == 1) {
            confirmImport(backup, backup.users[0])
            return
        }
        val labels = backup.users
            .map { "${it.name} · ${it.type.name} (${it.apps.size})" }
            .toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.import_source_title)
            .setItems(labels) { _, index -> confirmImport(backup, backup.users[index]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmImport(
        backup: com.arslan.clonecat.backup.Backup,
        saved: com.arslan.clonecat.backup.BackupUser
    ) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.import_confirm_title)
            .setMessage(getString(R.string.import_confirm_message, saved.apps.size, saved.name, user.label))
            .setPositiveButton(R.string.import_action) { _, _ -> runImport(backup, saved) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun runImport(
        backup: com.arslan.clonecat.backup.Backup,
        saved: com.arslan.clonecat.backup.BackupUser
    ) {
        binding.swipeRefresh.isRefreshing = true
        toast(getString(R.string.import_working))
        lifecycleScope.launch {
            val users = UserRepository.listUsers()
            val result = com.arslan.clonecat.backup.BackupRepository.restoreInto(
                this@UserDetailActivity,
                backup,
                saved,
                user,
                users
            )
            binding.swipeRefresh.isRefreshing = false
            val message = buildString {
                append(getString(R.string.import_installed, result.installed, result.alreadyThere))
                if (result.notOnDevice.isNotEmpty()) {
                    append("\n\n").append(getString(R.string.import_not_on_device))
                    append("\n").append(result.notOnDevice.joinToString("\n"))
                }
                if (result.failures.isNotEmpty()) {
                    append("\n\n").append(getString(R.string.import_failures))
                    append("\n").append(result.failures.joinToString("\n"))
                }
            }
            MaterialAlertDialogBuilder(this@UserDetailActivity)
                .setTitle(R.string.import_summary_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ -> load() }
                .show()
        }
    }

    private fun render() {
        val filtered = allApps
            .filter { showSystem || !it.isSystem }
            .filter { query.isEmpty() || it.packageName.contains(query, ignoreCase = true) }
        adapter.submit(filtered)
        binding.emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun launch(app: AppEntry) {
        lifecycleScope.launch {
            if (!user.running) UserRepository.startUser(user.id)
            if (user.type == UserType.PRIVATE) {
                val pin = PrivateCredentialStore.get(this@UserDetailActivity)
                if (!pin.isNullOrBlank()) {
                    com.arslan.clonecat.device.Device.run(
                        com.arslan.clonecat.cmd.AdbCommandBuilder.unlockUser(user.id, pin)
                    )
                }
            }
            val component = AppRepository.launcherComponent(this@UserDetailActivity, user.id, app.packageName)
            if (component == null) {
                toast(getString(R.string.no_launcher_activity))
                return@launch
            }
            val result = com.arslan.clonecat.device.Device.run(
                com.arslan.clonecat.cmd.AdbCommandBuilder.startActivity(user.id, component)
            )
            if (!result.success) toast(DeviceErrors.explain(result))
            else if (user.type == UserType.SECONDARY) toast(getString(R.string.secondary_launch_hint))
        }
    }

    private fun pickUserColor() {
        val row = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(48, 32, 48, 8)
        }
        lateinit var dialog: androidx.appcompat.app.AlertDialog
        UserColors.palette.forEach { color ->
            val swatch = android.view.View(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(color)
                }
                setOnClickListener {
                    UserColors.set(this@UserDetailActivity, user.id, color)
                    lifecycleScope.launch {
                        ShortcutRepository.refreshUser(this@UserDetailActivity, user)
                    }
                    dialog.dismiss()
                }
            }
            val size = (40 * resources.displayMetrics.density).toInt()
            row.addView(swatch, android.widget.LinearLayout.LayoutParams(size, size).apply {
                marginEnd = (8 * resources.displayMetrics.density).toInt()
            })
        }
        dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_user_color)
            .setView(row)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.show()
    }

    private fun pin(app: AppEntry) {
        lifecycleScope.launch {
            if (!ShortcutRepository.isSupported(this@UserDetailActivity)) {
                toast(getString(R.string.pin_unsupported))
                return@launch
            }
            val component = AppRepository.launcherComponent(this@UserDetailActivity, user.id, app.packageName)
            if (component == null) {
                toast(getString(R.string.no_launcher_activity))
                return@launch
            }
            val id = ShortcutRepository.idFor(user.id, app.packageName)
            val label = AppRepository.label(this@UserDetailActivity, user.id, app.packageName)
            val icon = AppRepository.icon(this@UserDetailActivity, user.id, app.packageName)
            editShortcut(id, label, icon, UserColors.of(this@UserDetailActivity, user.id, user.type).takeIf { user.id != 0 }) {
                lifecycleScope.launch {
                    val ok = ShortcutRepository.pin(this@UserDetailActivity, user, app.packageName, component)
                    toast(getString(if (ok) R.string.pin_requested else R.string.pin_unsupported))
                }
            }
        }
    }

    private fun pinAll() {
        val targets = allApps.filter { !it.isSystem }
        if (targets.isEmpty()) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pin_all_title)
            .setMessage(getString(R.string.pin_all_message, targets.size))
            .setPositiveButton(R.string.pin) { _, _ ->
                lifecycleScope.launch {
                    var pinned = 0
                    targets.forEach { app ->
                        val component = AppRepository.launcherComponent(
                            this@UserDetailActivity, user.id, app.packageName
                        ) ?: return@forEach
                        if (ShortcutRepository.pin(this@UserDetailActivity, user, app.packageName, component)) {
                            pinned++
                        }
                    }
                    toast(getString(R.string.pin_all_done, pinned))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmRemove(app: AppEntry) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.remove_title)
            .setMessage(getString(R.string.remove_message, app.packageName, user.label))
            .setPositiveButton(R.string.remove) { _, _ ->
                lifecycleScope.launch {
                    val result = AppRepository.uninstall(user.id, app.packageName)
                    if (!result.success) toast(DeviceErrors.explain(result)) else load()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openClonePicker() {
        clonePicker.launch(
            Intent(this, ClonePickerActivity::class.java)
                .putExtra(ClonePickerActivity.EXTRA_USER_LABEL, user.label)
                .putExtra(ClonePickerActivity.EXTRA_INSTALLED, allApps.map { it.packageName }.toTypedArray())
        )
    }

    private fun cloneInto(packages: List<String>) {
        if (packages.isEmpty()) return
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            val failures = mutableListOf<String>()
            packages.forEach { pkg ->
                val result = AppRepository.install(user.id, pkg)
                if (!result.success) failures.add("$pkg: ${DeviceErrors.explain(result)}")
            }
            binding.swipeRefresh.isRefreshing = false
            if (failures.isEmpty()) {
                toast(getString(R.string.clone_done, packages.size))
            } else {
                MaterialAlertDialogBuilder(this@UserDetailActivity)
                    .setTitle(R.string.clone_failed_title)
                    .setMessage(failures.joinToString("\n\n"))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            load()
        }
    }

    private fun confirmSwitch() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.switch_title)
            .setMessage(R.string.switch_message)
            .setPositiveButton(R.string.switch_action) { _, _ ->
                runUserAction { UserRepository.switchUser(user.id) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun runUserAction(action: suspend () -> com.arslan.clonecat.shell.ShellResult) {
        lifecycleScope.launch {
            val result = action()
            if (!result.success) toast(DeviceErrors.explain(result))
            else toast(getString(R.string.done))
        }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
