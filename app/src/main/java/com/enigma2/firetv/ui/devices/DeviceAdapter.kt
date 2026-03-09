package com.enigma2.firetv.ui.devices

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.DeviceProfile

class DeviceAdapter(
    private val onDeviceClick: (DeviceProfile) -> Unit,
    private val onDeviceLongClick: (DeviceProfile) -> Unit
) : ListAdapter<DeviceProfile, DeviceAdapter.ViewHolder>(DIFF) {

    private var activeId: String = ""

    fun setActiveId(id: String) {
        activeId = id
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), getItem(position).id == activeId)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tv_device_name)
        private val tvHost: TextView = itemView.findViewById(R.id.tv_device_host)
        private val ivActive: ImageView = itemView.findViewById(R.id.iv_active_indicator)

        fun bind(device: DeviceProfile, isActive: Boolean) {
            tvName.text = device.name
            val scheme = if (device.useHttps) "https" else "http"
            tvHost.text = "$scheme://${device.host}:${device.port}"
            ivActive.visibility = if (isActive) View.VISIBLE else View.INVISIBLE
            itemView.isSelected = isActive
            itemView.setOnClickListener { onDeviceClick(device) }
            itemView.setOnLongClickListener {
                onDeviceLongClick(device)
                true
            }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<DeviceProfile>() {
            override fun areItemsTheSame(a: DeviceProfile, b: DeviceProfile) = a.id == b.id
            override fun areContentsTheSame(a: DeviceProfile, b: DeviceProfile) = a == b
        }
    }
}
