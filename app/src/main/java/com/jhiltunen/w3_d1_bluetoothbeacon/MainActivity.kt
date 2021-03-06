package com.jhiltunen.w3_d1_bluetoothbeacon

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jhiltunen.w3_d1_bluetoothbeacon.ui.theme.W3_D1_BluetoothBeaconTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.HashMap

class MainActivity : ComponentActivity() {

    private var mBluetoothAdapter: BluetoothAdapter? = null

        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = bluetoothManager.adapter
        val viewModel = BluetoothLeViewModel()
        setContent {
            W3_D1_BluetoothBeaconTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    if (hasPermissions()) {
                        mBluetoothAdapter?.let { ShowDevices(mBluetoothAdapter = it, model = viewModel) }
                    }
                }
            }
        }
    }
    private fun hasPermissions(): Boolean {
        if (mBluetoothAdapter == null || !mBluetoothAdapter!!.isEnabled) {
            Log.d("DBG", "No Bluetooth LE capability")
            return false
        } else if (checkSelfPermission(ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d("DBG", "No fine location access")
            requestPermissions(arrayOf(ACCESS_FINE_LOCATION), 1);
            return true // assuming that the user grants permission
        }
        if (checkSelfPermission(BLUETOOTH_SERVICE) != PackageManager.PERMISSION_GRANTED) {
            Log.d("DBG", "No fine location access")
            requestPermissions(arrayOf(BLUETOOTH_SERVICE), 1);
            return true
        }
        return true
    }
}

@Composable
fun ShowDevices(mBluetoothAdapter: BluetoothAdapter, model: BluetoothLeViewModel) {
    val context = LocalContext.current
    val value: List<ScanResult>? by model.scanResults.observeAsState(null)
    val fScanning: Boolean by model.fScanning.observeAsState(false)

    Column(Modifier.padding(15.dp)) {
        Button(modifier = Modifier
            .height(50.dp)
            .fillMaxWidth(), onClick = {
            model.scanDevices(mBluetoothAdapter.bluetoothLeScanner)
        }, enabled = !fScanning) {
            if (fScanning) {
                Text(text = "Scanning in progress...")
            } else {
                Log.d("LIST", value.toString())
                Text(text = "Start scanning")
            }
        }
        Column {
            value?.forEach { scanResult ->
                ResultRow(result = scanResult)
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun ResultRow(result: ScanResult) {
    Log.d("COMPOSE", "$result")
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(text = "${result.device.address} ", modifier = Modifier.weight(fill = false, weight = 2f), color = if (!result.isConnectable) Color.Gray else Color.Black )
        result.device?.name?.let { Text(text = "$it | ", modifier = Modifier.weight(fill = false, weight = 2f), color = if (!result.isConnectable) Color.Gray else Color.Black ) }
        Text(text = "${result.rssi} dBm", modifier = Modifier.weight(fill = false, weight = 1f), color = if (!result.isConnectable) Color.Gray else Color.Black )
    }
}