package com.arslan.clonecat.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.arslan.clonecat.adapters.AppAdapter
import com.arslan.clonecat.adapters.AppGridAdapter
import com.arslan.clonecat.databinding.ActivityUserAppsBinding
import com.arslan.clonecat.device.AppEntry
import com.arslan.clonecat.device.AppRepository
import kotlinx.coroutines.launch

class UserAppsActivity : BaseActivity() {

    companion object {
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_USER_NAME = "user_name"
        const val EXTRA_USER_TYPE = "user_type"

        private val snapshots = mutableMapOf<Int, List<AppEntry>>()
    }

    private lateinit var binding: ActivityUserAppsBinding
    private lateinit var adapter: AppGridAdapter

    private var userId = 0
    private var allApps: List<AppEntry> = emptyList()
    private var showSystem = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        userId = intent.getIntExtra(EXTRA_USER_ID, 0)
        title = intent.getStringExtra(EXTRA_USER_NAME).orEmpty()

        adapter = AppGridAdapter(
            bindAsync = { app, apply ->
                lifecycleScope.launch {
                    apply(
                        AppAdapter.AppMetadata(
                            AppRepository.label(this@UserAppsActivity, userId, app.packageName),
                            AppRepository.icon(this@UserAppsActivity, userId, app.packageName)
                        )
                    )
                }
            },
            onClick = { app -> launch(app) }
        )
        binding.appGrid.layoutManager = GridLayoutManager(this, 4)
        binding.appGrid.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { load() }
        binding.systemChip.setOnCheckedChangeListener { _, checked ->
            showSystem = checked
            render()
        }

        val cached = snapshots[userId] ?: AppRepository.fastApps(this, userId)
        if (cached.isNotEmpty()) {
            allApps = cached
            render()
        }
        load()
    }

    private fun load() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            allApps = AppRepository.appsFor(userId)
            snapshots[userId] = allApps
            binding.swipeRefresh.isRefreshing = false
            render()
        }
    }

    private fun render() {
        val apps = allApps.filter { showSystem || !it.isSystem }
        AppRepository.prefetch(applicationContext, apps)
        adapter.submit(apps)
        binding.emptyView.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun launch(app: AppEntry) {
        startActivity(
            Intent(this, LaunchProxyActivity::class.java).apply {
                action = LaunchProxyActivity.ACTION_LAUNCH
                putExtra(LaunchProxyActivity.EXTRA_USER_ID, userId)
                putExtra(LaunchProxyActivity.EXTRA_PACKAGE, app.packageName)
                putExtra(LaunchProxyActivity.EXTRA_COMPONENT, "")
                putExtra(LaunchProxyActivity.EXTRA_USER_TYPE, intent.getStringExtra(EXTRA_USER_TYPE))
                putExtra(LaunchProxyActivity.EXTRA_USER_LABEL, intent.getStringExtra(EXTRA_USER_NAME))
            }
        )
    }
}
