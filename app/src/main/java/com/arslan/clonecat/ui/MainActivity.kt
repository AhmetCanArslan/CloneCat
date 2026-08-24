package com.arslan.clonecat.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.arslan.clonecat.R
import com.arslan.clonecat.adapters.UserAdapter
import com.arslan.clonecat.databinding.ActivityMainBinding
import com.arslan.clonecat.device.AppRepository
import com.arslan.clonecat.device.DeviceErrors
import com.arslan.clonecat.device.DeviceUser
import com.arslan.clonecat.device.PrivateCredentialStore
import com.arslan.clonecat.device.UserRepository
import com.arslan.clonecat.device.UserType
import com.arslan.clonecat.shell.ShizukuGate
import com.arslan.clonecat.shortcut.ShortcutRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: UserAdapter

    private var users: List<DeviceUser> = emptyList()

    private val clonePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val packages = result.data?.getStringArrayExtra(ClonePickerActivity.RESULT_PACKAGES).orEmpty()
        if (result.resultCode == RESULT_OK && packages.isNotEmpty()) pickTargetUsers(packages.toList())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = UserAdapter { user ->
            startActivity(
                Intent(this, UserDetailActivity::class.java)
                    .putExtra(UserDetailActivity.EXTRA_USER_ID, user.id)
                    .putExtra(UserDetailActivity.EXTRA_USER_NAME, user.label)
                    .putExtra(UserDetailActivity.EXTRA_USER_TYPE, user.type.name)
                    .putExtra(UserDetailActivity.EXTRA_USER_RUNNING, user.running)
            )
        }
        binding.userList.layoutManager = LinearLayoutManager(this)
        binding.userList.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { load() }
        binding.cloneFab.setOnClickListener {
            clonePicker.launch(Intent(this, ClonePickerActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        if (!ShizukuGate.isReady(this)) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        load()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_commands -> {
            CommandsSheet.show(this)
            true
        }
        R.id.action_setup -> {
            startActivity(Intent(this, SetupActivity::class.java))
            true
        }
        R.id.action_private_pin -> {
            showPrivatePinDialog()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showPrivatePinDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = getString(R.string.private_pin_hint)
            if (PrivateCredentialStore.has(this@MainActivity)) setText(PrivateCredentialStore.get(this@MainActivity))
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this).apply {
            setPadding(padding, 0, padding, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.private_pin_title)
            .setMessage(R.string.private_pin_message)
            .setView(container)
            .setPositiveButton(R.string.private_pin_save) { _, _ ->
                val pin = input.text.toString()
                if (pin.isNotBlank()) {
                    PrivateCredentialStore.save(this, pin)
                    toast(getString(R.string.private_pin_saved))
                }
            }
            .setNeutralButton(R.string.private_pin_clear) { _, _ ->
                PrivateCredentialStore.clear(this)
                toast(getString(R.string.private_pin_cleared))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun load() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            users = UserRepository.listUsers()
            adapter.submit(users)
            binding.emptyView.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
            binding.swipeRefresh.isRefreshing = false
            syncShortcuts(users)
        }
    }

    private fun pickTargetUsers(packages: List<String>) {
        val targets = users.filter { it.type != UserType.PRIMARY }
        if (targets.isEmpty()) {
            toast(getString(R.string.no_target_users))
            return
        }
        val labels = targets.map { "${it.label} · ${it.type.name}" }.toTypedArray()
        val checked = BooleanArray(targets.size)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.clone_into_users_title, packages.size))
            .setMultiChoiceItems(labels, checked) { _, index, isChecked -> checked[index] = isChecked }
            .setPositiveButton(R.string.clone) { _, _ ->
                cloneInto(targets.filterIndexed { index, _ -> checked[index] }, packages)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun cloneInto(targets: List<DeviceUser>, packages: List<String>) {
        if (targets.isEmpty()) return
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            val failures = mutableListOf<String>()
            var done = 0
            targets.forEach { user ->
                val installed = AppRepository.appsFor(user.id).map { it.packageName }.toSet()
                packages.filter { it !in installed }.forEach { pkg ->
                    val result = AppRepository.install(user.id, pkg)
                    if (result.success) done++
                    else failures.add("${user.label} · $pkg: ${DeviceErrors.explain(result)}")
                }
            }
            binding.swipeRefresh.isRefreshing = false
            if (failures.isEmpty()) {
                toast(getString(R.string.clone_done, done))
            } else {
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(R.string.clone_failed_title)
                    .setMessage(failures.joinToString("\n\n"))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    private suspend fun syncShortcuts(users: List<DeviceUser>) {
        val known = ShortcutRepository.ids(this)
        if (known.isEmpty()) return
        val touched = known.mapNotNull { it.removePrefix("u").substringBefore(':').toIntOrNull() }
            .toSet()
        val appsByUser = users.filter { it.id in touched }
            .associate { user -> user.id to AppRepository.appsFor(user.id).map { it.packageName }.toSet() }
        ShortcutRepository.sync(this, users, appsByUser)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
