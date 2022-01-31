package com.jhiltunen.w3_d1_bluetoothbeacon

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class BluetoothLeViewModel : ViewModel() {
    companion object GattAttributes {
        const val SCAN_PERIOD: Long = 5000
        const val STATE_DISCONNECTED = 0
        const val STATE_CONNECTING = 1
        const val STATE_CONNECTED = 2
        val UUID_HEART_RATE_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val UUID_HEART_RATE_SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val UUID_CLIENT_CHARACTERISTIC_CONFIG =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    val mBPM = MutableLiveData<Int>(0)
    val bpmList = MutableLiveData<MutableList<Int>>(ArrayList())
    val mConnectionState = MutableLiveData(-1)

    val scanResults = MutableLiveData<List<ScanResult>>(null)
    val fScanning = MutableLiveData<Boolean>(false)
    private val mResults = HashMap<String, ScanResult>()

    @SuppressLint("MissingPermission")
    fun scanDevices(scanner: BluetoothLeScanner) {
        viewModelScope.launch(Dispatchers.IO) {
            fScanning.postValue(true)
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .build()
            scanner.startScan(null, settings, leScanCallback)
            delay(SCAN_PERIOD)
            scanner.stopScan(leScanCallback)
            scanResults.postValue(mResults.values.distinct().toList())
            Log.d("scanDevices", "${scanResults.value}")
            fScanning.postValue(false)
        }
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            val deviceAddress = device.address
            mResults[deviceAddress] = result
            Log.d("DBG", "Device address: $deviceAddress (${result.isConnectable})")
        }
    }

    private var mBluetoothGatt: BluetoothGatt? = null

    @SuppressLint("MissingPermission")
    fun connectDevice(context: Context, device: BluetoothDevice) {
        Log.i("DBG", "Try to connect to the address ${device.address}")
        mConnectionState.postValue(STATE_CONNECTING)
        mBluetoothGatt = device.connectGatt(context, false, mGattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnectDevice() {
        mBluetoothGatt?.disconnect()
    }

    private val mGattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("DBG", "Connected GATT service")
                mConnectionState.postValue(STATE_CONNECTED)
                mBluetoothGatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mConnectionState.postValue(STATE_DISCONNECTED)
                gatt.close()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("DBG", "onServicesDiscovered()")
                for (gattService in gatt.services) {
                    Log.d("DBG", "Service ${gattService.uuid}")
                    if (gattService.uuid == UUID_HEART_RATE_SERVICE) {
                        Log.d("DBG", "BINGO!!!")
                        for (gattCharacteristic in gattService.characteristics) {
                            Log.d("DBG", "Characteristic ${gattCharacteristic.uuid}")
                            /* setup the system for the notification messages */
                            val characteristic =
                                gatt.getService(UUID_HEART_RATE_SERVICE).getCharacteristic(
                                    UUID_HEART_RATE_MEASUREMENT
                                )
                            gatt.setCharacteristicNotification(characteristic, true)

                            if (gatt.setCharacteristicNotification(characteristic, true)) {
                                // then enable them on the server
                                val descriptor =
                                    characteristic.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIG)
                                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                val writing = gatt.writeDescriptor(descriptor)
                                val bpm = characteristic.getIntValue(
                                    BluetoothGattCharacteristic.FORMAT_UINT16,
                                    1
                                )
                                Log.d("DBG", "BPM: $bpm")

                            }
                        }
                    }
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            Log.d("DBG", "onDescriptorWrite")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val bpm = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 1)
            Log.d("DBG", "BPM: $bpm")
            mBPM.postValue(bpm)
            bpmList.value?.add(bpm)
            // Call the extension function to notify
            bpmList.notifyObserver()
        }
    }

    // Kotlin Extension function to assign the LiveData to itself
    fun <T> MutableLiveData<T>.notifyObserver() {
        this.value = value
    }
}