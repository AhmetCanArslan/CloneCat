package com.arslan.clonecat.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.arslan.clonecat.databinding.ItemFolderAppBinding
import com.arslan.clonecat.device.AppEntry

class FolderAdapter(
    private val bindAsync: (AppEntry, (AppAdapter.AppMetadata) -> Unit) -> Unit,
    private val onClick: (AppEntry, android.view.View) -> Unit
) : RecyclerView.Adapter<FolderAdapter.Holder>() {

    private val apps = mutableListOf<AppEntry>()

    fun submit(items: List<AppEntry>) {
        apps.clear()
        apps.addAll(items)
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemFolderAppBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemFolderAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = apps.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = apps[position]
        holder.binding.appName.text = app.packageName
        holder.binding.appIcon.setImageDrawable(null)
        holder.itemView.tag = app.packageName

        bindAsync(app) { meta ->
            if (holder.itemView.tag != app.packageName) return@bindAsync
            holder.binding.appName.text = meta.label
            holder.binding.appIcon.setImageDrawable(meta.icon)
        }

        holder.itemView.setOnClickListener { onClick(app, holder.itemView) }
    }
}
