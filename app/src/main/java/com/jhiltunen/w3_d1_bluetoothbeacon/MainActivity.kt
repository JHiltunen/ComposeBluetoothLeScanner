package com.jhiltunen.w3_d1_bluetoothbeacon

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.jhiltunen.w3_d1_bluetoothbeacon.ui.theme.W3_D1_BluetoothBeaconTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.ArrayList
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
                        mBluetoothAdapter?.let { MainAppNav(mBluetoothAdapter = it, model = viewModel) }
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
fun MainAppNav(mBluetoothAdapter: BluetoothAdapter, model: BluetoothLeViewModel) {

    val navController = rememberNavController()
    NavHost (navController, startDestination = "main") {
        composable ("main") {
            Column {
                ShowDevices(mBluetoothAdapter = mBluetoothAdapter, model = model)
            }

        }
        composable ("graph") {
            GraphView(bluetoothViewModel = model)
        }

    }
}

@Composable
fun ShowDevices(mBluetoothAdapter: BluetoothAdapter, model: BluetoothLeViewModel) {
    val value: List<ScanResult>? by model.scanResults.observeAsState(null)
    val fScanning: Boolean by model.fScanning.observeAsState(false)
    val connectionState by model.mConnectionState.observeAsState(-1)

    val bpm: Int by model.mBPM.observeAsState(0)

    Column(
        Modifier
            .padding(15.dp)
            .fillMaxWidth(), verticalArrangement = Arrangement.Top) {
        Text(text = when(connectionState) {
            BluetoothLeViewModel.STATE_CONNECTED -> "Connected"
            BluetoothLeViewModel.STATE_CONNECTING -> "Connecting"
            BluetoothLeViewModel.STATE_DISCONNECTED -> "Disconnected"
            else -> ""
        })
        if (bpm != 0) {
            Text(text = "$bpm bpm", style = MaterialTheme.typography.h2, textAlign = TextAlign.Center, modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    model.disconnectDevice()
                })
        }
        Spacer(modifier = Modifier.size(15.dp))
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
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            value?.forEach { scanResult ->
                ResultRow(result = scanResult, bluetoothViewModel = model)
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun ResultRow(result: ScanResult, bluetoothViewModel: BluetoothLeViewModel) {
    val context = LocalContext.current
    Column {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (result.isConnectable) {
                    bluetoothViewModel.connectDevice(context, result.device)
                }
            }) {
            Text(text = "${result.device.address} ", modifier = Modifier.weight(fill = false, weight = 2f), color = if (!result.isConnectable) Color.Gray else Color.Black )
            result.device?.name?.let { Text(text = "$it | ", modifier = Modifier.weight(fill = false, weight = 2f), color = if (!result.isConnectable) Color.Gray else Color.Black ) }
            Text(text = "${result.rssi} dBm", modifier = Modifier.weight(fill = false, weight = 1f), color = if (!result.isConnectable) Color.Gray else Color.Black )
        }
    }
}

@Composable
fun GraphView(bluetoothViewModel: BluetoothLeViewModel) {
    val bpmList by bluetoothViewModel.bpmList.observeAsState()

    var entries: MutableList<Entry> = ArrayList()

    bpmList?.forEach{ bpm ->
        entries.add(Entry())
    }

    AndroidView (
        modifier = Modifier.fillMaxSize(),
        factory = { context: Context ->
            val view = LineChart(context)
            view.legend.isEnabled = false
            val data = LineData(LineDataSet( entries, "BPM"))
            val desc = Description()
            desc.text = "Beats Per Minute"
            view.description = desc;
            view.data = data
            view // return the view
        },
        update = { view ->
            // Update the view
            view.invalidate()
        }
    )

}