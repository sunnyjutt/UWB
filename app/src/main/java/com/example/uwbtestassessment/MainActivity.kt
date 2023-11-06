package com.example.uwbtestassessment

import android.Manifest
import android.bluetooth.BluetoothGatt
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
import androidx.core.uwb.*
import androidx.core.uwb.RangingParameters.Companion.CONFIG_UNICAST_DS_TWR
import androidx.core.uwb.RangingParameters.Companion.RANGING_UPDATE_RATE_FREQUENT
import androidx.core.uwb.rxjava3.controleeSessionScopeSingle
import androidx.core.uwb.rxjava3.controllerSessionScopeSingle
import androidx.core.uwb.rxjava3.rangingResultsFlowable
import androidx.recyclerview.widget.LinearLayoutManager
import com.clj.fastble.BleManager
import com.clj.fastble.callback.BleGattCallback
import com.clj.fastble.callback.BleNotifyCallback
import com.clj.fastble.callback.BleScanCallback
import com.clj.fastble.callback.BleWriteCallback
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import com.clj.fastble.scan.BleScanRuleConfig
import com.clj.fastble.utils.HexUtil
import com.example.uwbtestassessment.adapters.DeviceCallBack
import com.example.uwbtestassessment.adapters.FoundedDevicesAdapter
import com.example.uwbtestassessment.comm.ObserverManager
import com.example.uwbtestassessment.databinding.ActivityMainBinding
import com.example.uwbtestassessment.helpers.Extensions.changeColorStatusBar
import com.example.uwbtestassessment.helpers.UwbBleDevice
import com.example.uwbtestassessment.helpers.UwbDevCfg
import com.example.uwbtestassessment.helpers.UwbPhoneCfg
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subscribers.DisposableSubscriber
import java.util.*
import java.util.concurrent.TimeUnit

internal enum class AppStatus {
    IDLE, CONFIGURING, RANGING
}

class MainActivity : AppCompatActivity(), DeviceCallBack {

    private val REQUEST_CODE_PERMISSION_LOCATION = 2
    private val mUwbChannel = 9
    private val mUwbPreambleIndex = 10
    val PREFERRED_UWB_PROFILE_ID: Int = CONFIG_UNICAST_DS_TWR
    private val mPreferredUwbProfileId: Int = PREFERRED_UWB_PROFILE_ID
    var peerDevices: ArrayList<UwbDevice>? = null

    private lateinit var binding: ActivityMainBinding
    private var foundedDevicesAdapter = FoundedDevicesAdapter(this)
    private var scanning = false
    private var mUwbManager: UwbManager? = null
    private var controleeSessionScopeSingle: Single<UwbControleeSessionScope>? = null
    private var controleeSessionScope: UwbControleeSessionScope? = null
    private var appState: AppStatus? = null
    private val disposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        changeColorStatusBar(R.color.white)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setAdapter()
        initClicks()
    }

    private fun initViews() {
        peerDevices = ArrayList<UwbDevice>()
        appState = AppStatus.IDLE
        val packageManager = applicationContext.packageManager
        if (packageManager.hasSystemFeature("android.hardware.uwb")) {
            Log.d("Taggsg", "UWB hardware IS available!")
            createUwbManagerLocalAdapter()
        } else {
            Toast.makeText(applicationContext, "UWB hardware is not available", Toast.LENGTH_LONG)
                .show()
        }

        BleManager.getInstance().init(application)
        BleManager.getInstance()
            .enableLog(true)
            .setReConnectCount(1, 5000)
            .setConnectOverTime(20000)
            .setSplitWriteNum(50).operateTimeout = 5000
    }

    private fun initClicks() {
        binding.lvScan.setOnClickListener {
            checkBluetoothPermissions()
        }
    }

    private fun setAdapter() {
        binding.rvDevices.layoutManager = LinearLayoutManager(this)
        binding.rvDevices.adapter = foundedDevicesAdapter
    }

    private fun checkBluetoothPermissions() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (!bluetoothManager.adapter.isEnabled) {
            Toast.makeText(this, getString(R.string.please_turn_on_bluetooth), Toast.LENGTH_LONG)
                .show()
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
                Log.d(
                    "onRequestPermissionsResult",
                    "onRequestPermissionsResult: permission_granted"
                )
                initViews()
                setScanRule()
                startScan()
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

    private fun createUwbManagerLocalAdapter() {
        val t = Thread {
            mUwbManager = UwbManager.createInstance(applicationContext)
            controleeSessionScopeSingle = mUwbManager!!.controleeSessionScopeSingle()
            controleeSessionScope = controleeSessionScopeSingle!!.blockingGet()
            runOnUiThread {
                //Log.d(TAG, "run: " + localAddress.toString());
            }
        }
        t.start()
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
        } else {
            if (scanning) { // Stops scanning after a pre-defined scan period.
                scanning = false
                binding.progressBar.visibility = View.GONE
                bluetoothLeScanner.stopScan(scanCallback)
            } else {
                binding.progressBar.visibility = View.VISIBLE
                bluetoothLeScanner.startScan(scanCallback)
                foundedDevicesAdapter.clearScanDevice()

                Handler(Looper.getMainLooper()).postDelayed({
                    scanning = false
                    binding.progressBar.visibility = View.GONE
                    bluetoothLeScanner.stopScan(scanCallback)
                }, 10000)
                scanning = true
            }
        }
    }

    private fun connect(uwbBleDevice: UwbBleDevice) {
        val bleDevice = uwbBleDevice.bleDev
        BleManager.getInstance().connect(bleDevice, object : BleGattCallback() {
            override fun onStartConnect() {
                binding.progressBar.visibility = View.VISIBLE
            }

            override fun onConnectFail(bleDevice: BleDevice, exception: BleException) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.connect_fail),
                    Toast.LENGTH_LONG
                ).show()
            }

            override fun onConnectSuccess(bleDevice: BleDevice, gatt: BluetoothGatt, status: Int) {
                //connection successfull, stop the progressDialog
                binding.progressBar.visibility = View.GONE

                //create the object to put in our list of connected devices
                val dev = UwbBleDevice()
                dev.bleDev = bleDevice
                foundedDevicesAdapter.addDevice(dev)
                foundedDevicesAdapter.notifyDataSetChanged()
                BleManager.getInstance().notify(bleDevice,
                    "6E400001-B5A3-F393-E0A9-E50E24DCCA9E",
                    "6E400003-B5A3-F393-E0A9-E50E24DCCA9E",
                    object : BleNotifyCallback() {
                        override fun onNotifySuccess() {
                            appState = AppStatus.IDLE
                            BleManager.getInstance().write(
                                bleDevice,
                                "6E400001-B5A3-F393-E0A9-E50E24DCCA9E",
                                "6E400002-B5A3-F393-E0A9-E50E24DCCA9E",
                                HexUtil.hexStringToBytes("A5"),
                                object : BleWriteCallback() {
                                    override fun onWriteSuccess(
                                        current: Int,
                                        total: Int,
                                        justWrite: ByteArray
                                    ) {
                                        Log.d("TAG", "onWriteSuccess: ")
                                    }

                                    override fun onWriteFailure(exception: BleException) {
                                        Log.d("TAG", "onWriteFailure: ")
                                    }
                                })
                        }

                        override fun onNotifyFailure(exception: BleException) {}
                        override fun onCharacteristicChanged(data: ByteArray) {
                            when (appState) {
                                AppStatus.CONFIGURING -> {
                                    //parse the data sent from device
                                    val data2 = ByteArray(data.size - 1)
                                    System.arraycopy(data, 1, data2, 0, data.size - 1)
                                    val devConfig: UwbDevCfg = UwbDevCfg.fromByteArray(data2)

                                    //define the channel and preamble
                                    var uwbComplexChannel =
                                        UwbComplexChannel(mUwbChannel, mUwbPreambleIndex)
                                    val uwbControllerSessionScope: UwbControllerSessionScope?
                                    val uwbControleeSessionScope: UwbControleeSessionScope?
                                    val uwbAddress: UwbAddress
                                    val flowable: Flowable<RangingResult>
                                    val selectUwbDeviceRangingRole: Byte = 0 //Device is controller
                                    val selectUwbProfileId: Byte =
                                        PREFERRED_UWB_PROFILE_ID.toByte() //UNICAST DS TWR
                                    if (selectUwbDeviceRangingRole.toInt() == 0) {
                                        try {
                                            val blockingGet =
                                                mUwbManager!!.controleeSessionScopeSingle()
                                                    .blockingGet()
                                            uwbAddress = blockingGet.localAddress
                                            uwbControllerSessionScope = null
                                            uwbControleeSessionScope = blockingGet
                                        } catch (e: Exception) {
                                            return
                                        }
                                    } else {
                                        val blockingGet2 =
                                            mUwbManager!!.controllerSessionScopeSingle()
                                                .blockingGet()
                                        uwbAddress = blockingGet2.localAddress
                                        uwbControllerSessionScope = blockingGet2
                                        uwbComplexChannel = blockingGet2.uwbComplexChannel
                                        uwbControleeSessionScope = null
                                    }

                                    //generate a random sessionID
                                    val sessionId = Random().nextInt()

                                    //add the device to the list of peer devices
                                    val uwbDevice = UwbDevice(
                                        UwbAddress(
                                            devConfig.getDeviceMacAddress()
                                        )
                                    )
                                    peerDevices?.add(uwbDevice)

                                    //update our internal list of connected devices
                                    dev.uwbDevAddr = uwbDevice.address

                                    //setup the ranging parameters
                                    val sessionKey = byteArrayOf(
                                        0x8,
                                        0x7,
                                        0x1,
                                        0x2,
                                        0x3,
                                        0x4,
                                        0x5,
                                        0x6
                                    ) //that's what the firmware wants for VendorID and STS_IV
                                    val subSessionKey = byteArrayOf()
                                    //RangingParameters rangingParameters = new RangingParameters(selectUwbProfileId, sessionId,0,sessionKey, subSessionKey,uwbComplexChannel, peerDevices, RangingParameters.RANGING_UPDATE_RATE_AUTOMATIC);
                                    val rangingParameters = peerDevices?.let {
                                        RangingParameters(
                                            selectUwbProfileId.toInt(),
                                            sessionId,
                                            0,
                                            sessionKey,
                                            null,
                                            uwbComplexChannel,
                                            it,
                                            RANGING_UPDATE_RATE_FREQUENT
                                        )
                                    }

                                    //determine the flowable to use
                                    flowable = if (selectUwbDeviceRangingRole.toInt() == 0) {
                                        uwbControleeSessionScope!!.rangingResultsFlowable(
                                            rangingParameters!!
                                        )
                                    } else {
                                        uwbControllerSessionScope!!.rangingResultsFlowable(
                                            rangingParameters!!
                                        )
                                    }

                                    //generate the config to send to firmware
                                    val uwbPhoneCfg = UwbPhoneCfg()
                                    uwbPhoneCfg.specVerMajor =
                                        256.toShort() //this is 1 sent in Little Endian
                                    uwbPhoneCfg.specVerMinor = 0.toShort()
                                    uwbPhoneCfg.sessionId = sessionId
                                    uwbPhoneCfg.preambleIndex =
                                        uwbComplexChannel.preambleIndex.toByte()
                                    uwbPhoneCfg.channel = uwbComplexChannel.channel.toByte()
                                    uwbPhoneCfg.profileId = selectUwbProfileId
                                    uwbPhoneCfg.deviceRangingRole =
                                        (1 shl selectUwbDeviceRangingRole.toInt()).toByte()
                                    uwbPhoneCfg.phoneMacAddress = uwbAddress.address
                                    val cmd = ByteArray(uwbPhoneCfg.toByteArray().size + 1)
                                    cmd[0] = 0x0B
                                    System.arraycopy(
                                        uwbPhoneCfg.toByteArray(),
                                        0,
                                        cmd,
                                        1,
                                        uwbPhoneCfg.toByteArray().size
                                    )

                                    //application will go into ranging mode
                                    appState = AppStatus.RANGING

                                    //send the config data to device
                                    BleManager.getInstance().write(
                                        bleDevice,
                                        "6E400001-B5A3-F393-E0A9-E50E24DCCA9E",
                                        "6E400002-B5A3-F393-E0A9-E50E24DCCA9E",
                                        cmd,  //HexUtil.hexStringToBytes("0B"). 010001001945559ED400000B090600100EE6010349646D67B36BDEE4C800"),
                                        object : BleWriteCallback() {
                                            override fun onWriteSuccess(
                                                current: Int,
                                                total: Int,
                                                justWrite: ByteArray
                                            ) {
                                                appState = AppStatus.RANGING
                                                startRanging(
                                                    foundedDevicesAdapter,
                                                    dev,
                                                    uwbPhoneCfg,
                                                    rangingParameters,
                                                    flowable
                                                )
                                            }

                                            override fun onWriteFailure(exception: BleException) {
                                                Log.d("TAG", "onWriteFailure: ")
                                            }
                                        })
                                }

                                else -> {}
                            }
                        }
                    }
                )
            }

            override fun onDisConnected(
                isActiveDisConnected: Boolean,
                bleDevice: BleDevice,
                gatt: BluetoothGatt,
                status: Int
            ) {
                binding.progressBar.visibility = View.GONE
                foundedDevicesAdapter.removeDevice(bleDevice)
                foundedDevicesAdapter.notifyDataSetChanged()
                stopRanging()
                if (isActiveDisConnected) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.disconnected),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.disconnected),
                        Toast.LENGTH_LONG
                    ).show()
                    ObserverManager.getInstance().notifyObserver(bleDevice)
                }
            }
        })
    }

    fun startRanging(
        DA: FoundedDevicesAdapter,
        dev: UwbBleDevice,
        phoneConfig: UwbPhoneCfg?,
        rangingParameters: RangingParameters?,
        rangingResultFlowable: Flowable<RangingResult>
    ) {
        val t = Thread {
            val disposable = rangingResultFlowable
                .delay(100, TimeUnit.MILLISECONDS)
                .subscribeWith(object : DisposableSubscriber<RangingResult?>() {
                    override fun onStart() {
                        request(1)
                    }

                    override fun onNext(rangingResult: RangingResult?) {
                        if (rangingResult is RangingResult.RangingResultPosition) {
                            val rangingResultPosition: RangingResult.RangingResultPosition =
                                rangingResult as RangingResult.RangingResultPosition
                            val pos: RangingPosition =
                                rangingResultPosition.position
                            if (pos != null) { //rangingResultPosition.getPosition().getDistance() != null) {
                                var distance: Float
                                var azimuth: Float
                                var elevation: Float
                                distance = 0f
                                azimuth = 0f
                                elevation = 0f
                                if (pos.distance != null) distance = pos.distance!!.value
                                if (pos.azimuth != null) azimuth = pos.azimuth!!.value
                                if (pos.elevation != null) elevation = pos.elevation!!.value
                                val d: UwbBleDevice? =
                                    foundedDevicesAdapter.findByUwbAddr(rangingResult.device.address)
                                if (d != null) {
                                    d.uwbPosition = pos //rangingResultPosition;
                                    foundedDevicesAdapter.addDevice(d)
                                    runOnUiThread { foundedDevicesAdapter.notifyDataSetChanged() }
                                }
                            }
                        } else {
                            if (rangingResult is RangingResult.RangingResultPeerDisconnected) {
                                val r: RangingResult.RangingResultPeerDisconnected =
                                    rangingResult as RangingResult.RangingResultPeerDisconnected
                                stopRanging()
                                dispose()
                            }
                        }
                        request(1)
                    }

                    override fun onError(t: Throwable) {
                        //doSomethingWithError(t);
                        t.printStackTrace()
                    }

                    override fun onComplete() {

                    }
                }) as Disposable
        }
        dev.disposable = disposable
        DA.addDevice(dev)
        t.start()
    }

    fun stopRanging() {
        disposable?.dispose()
    }

    override fun invoke(p1: UwbBleDevice) {
        foundedDevicesAdapter.setOnDeviceClickListener(object :
            FoundedDevicesAdapter.OnDeviceClickListener {
            override fun onConnect(bleDevice: UwbBleDevice?) {
                if (!BleManager.getInstance().isConnected(bleDevice!!.bleDev)) {
                    BleManager.getInstance().cancelScan()
                    connect(bleDevice)
                }
            }

            override fun onDisConnect(bleDevice: UwbBleDevice?) {

            }

        })
    }
}