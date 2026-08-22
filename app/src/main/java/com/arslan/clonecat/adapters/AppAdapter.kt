package com.arslan.clonecat.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.arslan.clonecat.R
import com.arslan.clonecat.databinding.ItemAppBinding
import com.arslan.clonecat.device.AppEntry

class AppAdapter(
    private val bindAsync: (AppEntry, (AppMetadata) -> Unit) -> Unit,
    private val onLaunch: (AppEntry) -> Unit,
    private val onPin: (AppEntry) -> Unit,
    private val onRemove: (AppEntry) -> Unit
) : RecyclerView.Adapter<AppAdapter.Holder>() {

    data class AppMetadata(val label: String, val icon: android.graphics.drawable.Drawable?)

    private val apps = mutableListOf<AppEntry>()

    fun submit(items: List<AppEntry>) {
        apps.clear()
        apps.addAll(items)
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = apps.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = apps[position]
        val context = holder.itemView.context
        holder.binding.appName.text = app.packageName
        holder.binding.appMeta.text =
            context.getString(R.string.app_meta, app.packageName, app.uid)
        holder.binding.appIcon.setImageDrawable(null)
        holder.itemView.tag = app.packageName

        bindAsync(app) { meta ->
            if (holder.itemView.tag != app.packageName) return@bindAsync
            holder.binding.appName.text = meta.label
            holder.binding.appIcon.setImageDrawable(meta.icon)
        }

        holder.binding.launchButton.setOnClickListener { onLaunch(app) }
        holder.binding.pinButton.setOnClickListener { onPin(app) }
        holder.binding.removeButton.setOnClickListener { onRemove(app) }
    }
}
