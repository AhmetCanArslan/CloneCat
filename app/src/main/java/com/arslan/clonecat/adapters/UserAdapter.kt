package com.arslan.clonecat.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.arslan.clonecat.R
import com.arslan.clonecat.databinding.ItemUserBinding
import com.arslan.clonecat.device.DeviceUser
import com.arslan.clonecat.shortcut.ShortcutIcon

class UserAdapter(
    private val onClick: (DeviceUser) -> Unit,
    private val onPinFolder: (DeviceUser) -> Unit = {}
) : RecyclerView.Adapter<UserAdapter.Holder>() {

    private val users = mutableListOf<DeviceUser>()

    fun submit(items: List<DeviceUser>) {
        users.clear()
        users.addAll(items)
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = users.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val user = users[position]
        val context = holder.itemView.context
        holder.binding.userName.text = user.label
        holder.binding.userMeta.text = context.getString(
            R.string.user_meta,
            user.id,
            if (user.running) context.getString(R.string.state_running)
            else context.getString(R.string.state_stopped)
        )
        holder.binding.typeChip.text = user.type.name
        holder.binding.typeChip.chipBackgroundColor =
            android.content.res.ColorStateList.valueOf(ShortcutIcon.colorFor(user.type))
        holder.binding.folderButton.setOnClickListener { onPinFolder(user) }
        holder.itemView.setOnClickListener { onClick(user) }
    }
}
