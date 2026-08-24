package com.arslan.clonecat.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.arslan.clonecat.databinding.ItemGlyphIconBinding

class GlyphPickAdapter(
    private val glyphs: List<Int>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<GlyphPickAdapter.Holder>() {

    class Holder(val binding: ItemGlyphIconBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemGlyphIconBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = glyphs.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val res = glyphs[position]
        holder.binding.iconImage.setImageResource(res)
        holder.itemView.setOnClickListener { onClick(res) }
    }
}
