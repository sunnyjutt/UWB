package com.example.uwbtestassessment

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.clj.fastble.BleManager
import com.clj.fastble.callback.BleScanCallback
import com.clj.fastble.data.BleDevice
import com.clj.fastble.scan.BleScanRuleConfig
import com.example.uwbtestassessment.adapters.FoundedDevicesAdapter
import com.example.uwbtestassessment.databinding.ActivityMainBinding
import com.example.uwbtestassessment.helpers.Extensions.changeColorStatusBar
import com.example.uwbtestassessment.helpers.UwbBleDevice
import java.util.*

class MainActivity : AppCompatActivity() {

    private val REQUEST_CODE_PERMISSION_LOCATION = 2

    private lateinit var binding: ActivityMainBinding
    private var foundedDevicesAdapter = FoundedDevicesAdapter()
    private var scanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        changeColorStatusBar(R.color.white)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setAdapter()
        initClicks()
    }

    private fun initClicks() {
        binding.lvScan.setOnClickListener {
            checkBluetoothPermissions()
        }
    }

    private fun setAdapter(){
        binding.rvDevices.layoutManager = LinearLayoutManager(this)
        binding.rvDevices.adapter = foundedDevicesAdapter
    }

    private fun checkBluetoothPermissions(){
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (!bluetoothManager.adapter.isEnabled) {
            Toast.makeText(this, getString(R.string.please_turn_on_bluetooth), Toast.LENGTH_LONG).show()
            return
        }

        // Check if you have the necessary permissions. Request them if not.
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.UWB_RANGING,  /* Manifest.permission.NEARBY_WIFI_DEVICES,*/
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val permissionDeniedList: MutableList<String> = ArrayList()
        for (permission in permissions) {
            val permissionCheck = ContextCompat.checkSelfPermission(this, permission)
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                Log.d("onRequestPermissionsResult", "onRequestPermissionsResult: permission_granted")
                setScanRule()
                scanLeDevice()
            } else {
                permissionDeniedList.add(permission)
            }
        }
        if (!permissionDeniedList.isEmpty()) {
            val deniedPermissions = permissionDeniedList.toTypedArray()
            ActivityCompat.requestPermissions(
                this,
                deniedPermissions,
                REQUEST_CODE_PERMISSION_LOCATION
            )
        }
    }

    private fun setScanRule() {
        val serviceUuids: Array<UUID>? = null
        val names = arrayOf("TS_DCU150", "TS_DCU040", "NXP_SR040", "NXP_SR150", "NXP_SR160")
        val scanRuleConfig = BleScanRuleConfig.Builder()
            .setServiceUuids(serviceUuids)
            .setDeviceName(true, *names)
            .setDeviceMac("")
            .setAutoConnect(false)
            .setScanTimeOut(10000)
            .build()
        BleManager.getInstance().initScanRule(scanRuleConfig)
    }

    private fun startScan() {
        BleManager.getInstance().scan(object : BleScanCallback() {
            override fun onScanStarted(success: Boolean) {
                Log.d("onRequestPermissionsResult", "onScanStarted: ")
                binding.progressBar.visibility = View.VISIBLE
                foundedDevicesAdapter.clearScanDevice()
            }

            override fun onLeScan(bleDevice: BleDevice) {
                Log.d("onRequestPermissionsResult", "onLeScan: ")
                super.onLeScan(bleDevice)
            }

            override fun onScanning(bleDevice: BleDevice) {
                val dev = UwbBleDevice()
                dev.bleDev = bleDevice
                foundedDevicesAdapter.addDevice(dev)
                Log.d("onRequestPermissionsResult", "onScanning: $dev")
            }

            override fun onScanFinished(scanResultList: List<BleDevice>) {
                Log.d("onRequestPermissionsResult", "onScanFinished: $scanResultList")
                binding.progressBar.visibility = View.GONE
            }
        })
    }

    private fun scanLeDevice() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothLeScanner = bluetoothManager.adapter.bluetoothLeScanner

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                // Handle discovered UWB-enabled devices
//                foundedDevicesAdapter.addDevice(result?.device)
                Log.d("onRequestPermissionsResult", "onScanResult: $result")
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.d("onRequestPermissionsResult", "onScanFailed: ")
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Permission is not granted", Toast.LENGTH_SHORT).show()
            return
        }else{
            if (scanning) { // Stops scanning after a pre-defined scan period.
                scanning = false
                binding.progressBar.visibility = View.GONE
                bluetoothLeScanner.stopScan(scanCallback)
            } else {
                Handler(Looper.getMainLooper()).postDelayed({
                    scanning = false
                    binding.progressBar.visibility = View.GONE
                    bluetoothLeScanner.stopScan(scanCallback)
                }, 10000)
                scanning = true
                binding.progressBar.visibility = View.VISIBLE
                bluetoothLeScanner.startScan(scanCallback)
                foundedDevicesAdapter.clearScanDevice()
            }
        }
    }
}