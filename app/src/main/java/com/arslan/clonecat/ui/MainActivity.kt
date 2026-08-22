package com.arslan.clonecat.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.arslan.clonecat.R
import com.arslan.clonecat.adapters.UserAdapter
import com.arslan.clonecat.databinding.ActivityMainBinding
import com.arslan.clonecat.device.AppRepository
import com.arslan.clonecat.device.DeviceUser
import com.arslan.clonecat.device.UserRepository
import com.arslan.clonecat.shell.ShizukuGate
import com.arslan.clonecat.shortcut.ShortcutRepository
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: UserAdapter

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
        R.id.action_refresh -> {
            load()
            true
        }
        R.id.action_commands -> {
            CommandsSheet.show(this)
            true
        }
        R.id.action_setup -> {
            startActivity(Intent(this, SetupActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun load() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            val users = UserRepository.listUsers()
            adapter.submit(users)
            binding.emptyView.visibility = if (users.isEmpty()) android.view.View.VISIBLE
            else android.view.View.GONE
            binding.swipeRefresh.isRefreshing = false
            syncShortcuts(users)
        }
    }

    /** Keeps pinned shortcuts in step with what is actually installed. */
    private suspend fun syncShortcuts(users: List<DeviceUser>) {
        val known = ShortcutRepository.ids(this)
        if (known.isEmpty()) return
        val touched = known.mapNotNull { it.removePrefix("u").substringBefore(':').toIntOrNull() }
            .toSet()
        val appsByUser = users.filter { it.id in touched }
            .associate { user -> user.id to AppRepository.appsFor(user.id).map { it.packageName }.toSet() }
        ShortcutRepository.sync(this, users, appsByUser)
    }
}
