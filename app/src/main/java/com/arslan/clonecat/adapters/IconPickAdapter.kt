package com.arslan.clonecat.adapters

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.arslan.clonecat.databinding.ItemAppGridBinding

class IconPickAdapter(
    private val icons: List<Pair<String, Drawable>>,
    private val onClick: (Drawable) -> Unit
) : RecyclerView.Adapter<IconPickAdapter.Holder>() {

    class Holder(val binding: ItemAppGridBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemAppGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = icons.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val (label, icon) = icons[position]
        holder.binding.appName.text = label
        holder.binding.appIcon.setImageDrawable(icon)
        holder.itemView.setOnClickListener { onClick(icon) }
    }
}
