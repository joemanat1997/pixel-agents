package com.example.applocker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.applocker.databinding.ItemAppBinding

class AppListAdapter(
    private val apps: MutableList<InstalledApp>,
    private val onToggle: (InstalledApp, Boolean) -> Unit
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    inner class AppViewHolder(val binding: ItemAppBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.binding.appIcon.setImageDrawable(app.icon)
        holder.binding.appLabel.text = app.label
        holder.binding.packageName.text = app.packageName

        // Detach listener before setting state to avoid recycled-view callbacks.
        holder.binding.lockSwitch.setOnCheckedChangeListener(null)
        holder.binding.lockSwitch.isChecked = app.locked
        holder.binding.lockSwitch.setOnCheckedChangeListener { _, isChecked ->
            app.locked = isChecked
            onToggle(app, isChecked)
        }

        holder.binding.root.setOnClickListener {
            holder.binding.lockSwitch.toggle()
        }
    }

    override fun getItemCount(): Int = apps.size

    fun submit(newApps: List<InstalledApp>) {
        apps.clear()
        apps.addAll(newApps)
        notifyDataSetChanged()
    }
}
