package com.arslan.clonecat.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.arslan.clonecat.R
import com.arslan.clonecat.adapters.AppAdapter
import com.arslan.clonecat.adapters.FolderAdapter
import com.arslan.clonecat.databinding.ActivityFolderBinding
import com.arslan.clonecat.device.AppEntry
import com.arslan.clonecat.device.AppRepository
import kotlinx.coroutines.launch

/**
 * Launcher-style folder popup: a fixed-size panel over the blurred home
 * screen showing the user's apps on 4x3 pages. Tapping an app launches it
 * in that user; tapping outside the panel closes it.
 */
class FolderActivity : BaseActivity() {

    companion object {
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_USER_NAME = "user_name"
        const val EXTRA_USER_TYPE = "user_type"

        private const val COLUMNS = 3
        private const val ROWS = 4
        private const val PAGE_SIZE = COLUMNS * ROWS
    }

    private lateinit var binding: ActivityFolderBinding
    private lateinit var pagesAdapter: PagesAdapter

    private var userId = 0
    private var allApps: List<AppEntry> = emptyList()
    private var showSystem = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyBlurBehind()
        binding = ActivityFolderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getIntExtra(EXTRA_USER_ID, 0)
        binding.folderTitle.text = intent.getStringExtra(EXTRA_USER_NAME).orEmpty()

        pagesAdapter = PagesAdapter()
        binding.appPager.adapter = pagesAdapter
        binding.appPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = highlightDot(position)
        })

        binding.systemTick.setOnCheckedChangeListener { _, checked ->
            showSystem = checked
            render()
        }
        binding.folderCard.setOnClickListener { /* keep open when tapping inside */ }
        binding.folderScrim.setOnClickListener { finish() }

        load()
    }

    /** Blurs the wallpaper behind the folder window on Android 12+, dim otherwise. */
    private fun applyBlurBehind() {
        window.setDimAmount(0.25f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply {
                blurBehindRadius = (14 * resources.displayMetrics.density).toInt()
            }
        } else {
            window.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    private fun load() {
        lifecycleScope.launch {
            allApps = AppRepository.appsFor(userId)
            render()
        }
    }

    private fun render() {
        val apps = allApps.filter { showSystem || !it.isSystem }
        val pages = apps.chunked(PAGE_SIZE)
        pagesAdapter.submit(pages)

        val empty = apps.isEmpty()
        binding.emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        binding.folderCard.visibility = if (empty) View.GONE else View.VISIBLE
        setupDots(pages.size)
    }

    private fun setupDots(count: Int) {
        with(binding.dotsRow) {
            removeAllViews()
            visibility = if (count > 1) View.VISIBLE else View.GONE
            repeat(count) { index ->
                addView(View(context).apply {
                    val size = (6 * resources.displayMetrics.density).toInt()
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        leftMargin = (4 * resources.displayMetrics.density).toInt()
                        rightMargin = (4 * resources.displayMetrics.density).toInt()
                    }
                    setBackgroundResource(R.drawable.dot_indicator)
                    tag = index
                })
            }
        }
        highlightDot(binding.appPager.currentItem)
    }

    private fun highlightDot(position: Int) {
        val color = TypedValue().also { theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, it, true) }.data
        for (i in 0 until binding.dotsRow.childCount) {
            val dot = binding.dotsRow.getChildAt(i)
            dot.background.setTint(color)
            dot.alpha = if (i == position) 1f else 0.35f
        }
    }

    private fun bindApp(app: AppEntry, apply: (AppAdapter.AppMetadata) -> Unit) {
        lifecycleScope.launch {
            apply(
                AppAdapter.AppMetadata(
                    AppRepository.label(this@FolderActivity, userId, app.packageName),
                    AppRepository.icon(this@FolderActivity, userId, app.packageName)
                )
            )
        }
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

    /** One folder page = one 4x3 grid of apps, swiped horizontally. */
    private inner class PagesAdapter : RecyclerView.Adapter<PageHolder>() {

        private val pages: MutableList<List<AppEntry>> = mutableListOf()

        fun submit(items: List<List<AppEntry>>) {
            pages.clear()
            pages.addAll(items)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_folder_page, parent, false)
            return PageHolder(view as RecyclerView)
        }

        override fun getItemCount() = pages.size

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            holder.grid.layoutManager = GridLayoutManager(holder.grid.context, COLUMNS)
            holder.grid.adapter = FolderAdapter(
                bindAsync = { app, apply -> bindApp(app, apply) },
                onClick = { app -> launch(app) }
            ).apply { submit(pages[position]) }
        }
    }

    class PageHolder(val grid: RecyclerView) : RecyclerView.ViewHolder(grid)
}
