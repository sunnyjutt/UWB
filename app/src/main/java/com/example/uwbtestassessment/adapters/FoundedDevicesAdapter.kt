package com.example.uwbtestassessment.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.clj.fastble.BleManager
import com.clj.fastble.data.BleDevice
import com.example.uwbtestassessment.databinding.CusDeviceAdpaterItemBinding
import com.example.uwbtestassessment.helpers.UwbBleDevice

typealias DeviceCallBack = (UwbBleDevice) -> Unit

class FoundedDevicesAdapter : RecyclerView.Adapter<FoundedDevicesAdapter.DeviceHolder>() {

    private val list = ArrayList<UwbBleDevice>()

    inner class DeviceHolder(private val binding: CusDeviceAdpaterItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun onBind(item: UwbBleDevice) {


//            binding.ivLargeImage.setOnClickListener {
//                handleProductClick(item.largeImageURL)
//            }
        }

        private fun handleProductClick(largeImageURL: String?) {

        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceHolder {
        val binding =
            CusDeviceAdpaterItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceHolder(binding)
    }

    override fun getItemCount(): Int {
        return list.size ?: 0
    }

    override fun onBindViewHolder(holder: DeviceHolder, position: Int) {
        val item = list.getOrNull(position)
        item?.let {
            holder.onBind(it)
        }
    }

    fun addDevice(bleDevice: UwbBleDevice?) {
        if (bleDevice != null) {
            removeDevice(bleDevice)
            list.add(bleDevice)
            notifyDataSetChanged()
        }
    }

    fun removeDevice(bleDevice: UwbBleDevice) {
        if (list.isNotEmpty()) {
            for (i in list.indices) {
                val device: BleDevice = list[i].bleDev
                if (bleDevice.bleDev.key.equals(device.key)) {
                    list.removeAt(i)
                }
            }
        }
    }

    fun removeDevice(bleDevice: BleDevice) {
        if (list.isNotEmpty()) {
            for (i in list.indices) {
                val device: BleDevice = list[i].bleDev
                if (bleDevice.key == device.key) {
                    list.removeAt(i)
                }
            }
        }
    }

    fun clearConnectedDevice() {
        if (list.isNotEmpty()) {
            for (i in list.indices) {
                val device: BleDevice = list[i].bleDev
                if (BleManager.getInstance().isConnected(device)) {
                    list.removeAt(i)
                }
            }
        }
    }

    fun clearScanDevice() {
        if (list.isNotEmpty()) {
            for (i in list.indices) {
                val device: BleDevice = list.get(i).bleDev
                if (!BleManager.getInstance().isConnected(device)) {
                    list.removeAt(i)
                }
            }
            notifyDataSetChanged()
        }
    }
}