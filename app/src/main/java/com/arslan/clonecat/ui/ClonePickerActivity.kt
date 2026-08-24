package com.arslan.clonecat.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.arslan.clonecat.R
import com.arslan.clonecat.adapters.PickApp
import com.arslan.clonecat.adapters.PickAppAdapter
import com.arslan.clonecat.adapters.loadPickApps
import com.arslan.clonecat.databinding.ActivityClonePickerBinding
import kotlinx.coroutines.launch

class ClonePickerActivity : BaseActivity() {

    companion object {
        const val EXTRA_USER_LABEL = "user_label"
        const val EXTRA_INSTALLED = "installed"
        const val RESULT_PACKAGES = "packages"
    }

    private lateinit var binding: ActivityClonePickerBinding
    private lateinit var adapter: PickAppAdapter

    private val selected = linkedSetOf<String>()
    private val installed by lazy { intent.getStringArrayExtra(EXTRA_INSTALLED).orEmpty().toSet() }
    private var candidates: List<PickApp> = emptyList()
    private var systemFlags: Map<String, Boolean> = emptyMap()
    private var showSystem = false
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClonePickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val target = intent.getStringExtra(EXTRA_USER_LABEL)
        title = if (target.isNullOrBlank()) getString(R.string.select_apps_title)
        else getString(R.string.clone_title, target)

        adapter = PickAppAdapter(selected, installed) { updateFab() }
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
        binding.cloneFab.setOnClickListener {
            setResult(
                RESULT_OK,
                Intent().putExtra(RESULT_PACKAGES, selected.toTypedArray())
            )
            finish()
        }

        load()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun load() {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val (list, system) = loadPickApps(this@ClonePickerActivity, emptySet())
            candidates = list
            systemFlags = system
            binding.progress.visibility = View.GONE
            render()
        }
    }

    private fun render() {
        val filtered = candidates
            .filter { showSystem || systemFlags[it.packageName] != true }
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
        binding.cloneFab.text = getString(R.string.clone_selected, selected.size)
        binding.cloneFab.visibility = if (selected.isEmpty()) View.GONE else View.VISIBLE
    }
}
