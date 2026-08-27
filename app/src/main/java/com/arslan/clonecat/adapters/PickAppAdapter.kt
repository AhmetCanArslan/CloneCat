package com.arslan.clonecat.adapters

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.arslan.clonecat.R
import com.arslan.clonecat.databinding.ItemPickAppBinding
import com.arslan.clonecat.device.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PickApp(val packageName: String, val label: String, val icon: Drawable?)

class PickAppAdapter(
    private val selected: MutableSet<String>,
    private val locked: Set<String> = emptySet(),
    private val onToggle: () -> Unit
) : RecyclerView.Adapter<PickAppAdapter.Holder>() {

    private val apps = mutableListOf<PickApp>()

    fun submit(items: List<PickApp>) {
        apps.clear()
        apps.addAll(items)
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemPickAppBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemPickAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = apps.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = apps[position]
        val context = holder.itemView.context
        holder.binding.appName.text = app.label
        holder.binding.appIcon.setImageDrawable(app.icon)
        val isLocked = app.packageName in locked
        holder.binding.check.setOnCheckedChangeListener(null)
        holder.binding.check.isChecked = isLocked || app.packageName in selected
        holder.binding.check.isEnabled = !isLocked
        holder.binding.appMeta.text =
            if (isLocked) context.getString(R.string.already_cloned) else app.packageName
        holder.binding.check.setOnCheckedChangeListener { _, checked ->
            if (checked) selected.add(app.packageName) else selected.remove(app.packageName)
            onToggle()
        }
        holder.itemView.setOnClickListener { if (!isLocked) holder.binding.check.toggle() }
    }
}

suspend fun loadPickApps(context: Context, exclude: Set<String>): Pair<List<PickApp>, Map<String, Boolean>> {
    val apps = AppRepository.appsFor(0).filter { it.packageName !in exclude }
    val system = apps.associate { it.packageName to it.isSystem }
    val list = withContext(Dispatchers.IO) {
        apps.map { app ->
            PickApp(
                app.packageName,
                AppRepository.label(context, 0, app.packageName),
                AppRepository.icon(context, 0, app.packageName)
            )
        }.sortedBy { it.label.lowercase() }
    }
    return list to system
}
