package grey.example.android.ble.ble

import android.bluetooth.BluetoothAdapter.LeScanCallback
import android.bluetooth.BluetoothAdapter.getDefaultAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import grey.example.android.ble.R
import grey.example.android.ble.Strings
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*


class BLEprocessing(val ble: GATToverCoroutines) {

    private val uuidCmdChar = UUID.fromString("0000fe41-8e22-4541-9d4c-21edae82ed19")

    private var BLEservices: List<BluetoothGattService> = emptyList()

    private var scanJob: Job = Job() //TODO Coroutine scope and global job

    var filterString: List<String>? = null

    private var tempListOfDevices = emptySet<BluetoothDevice>().toMutableSet()
    private var _devices = MutableLiveData<Set<BluetoothDevice>>()
    val devices: LiveData<Set<BluetoothDevice>>
        get() = _devices


    private var _isScan = MutableLiveData<Boolean>(false)
    val isScan: LiveData<Boolean>
        get() = _isScan

    private var scanCallback = LeScanCallback { device, k, t ->
        filterString?.let {
            for (item in it) {
                if (device.name == item) {
                    addDevice(device)
                    break
                }
            }
        } ?: addDevice(device)
    }

    fun startScan() {
        if (isScan.value == true) {
            stopScan()
        } else {
            zeroDevices()
            scanJob = GlobalScope.launch {
                getDefaultAdapter().startLeScan(scanCallback)
                delay(10000)
                stopScan()
            }
        }
        _isScan.value = !isScan.value!!
    }

    fun stopScan() {
        getDefaultAdapter().stopLeScan(scanCallback)
        _isScan.postValue(false)
        scanJob.cancel()
    }

    /**
     * This function set filter by name
     * @param str - String with names wich you want to filter delimiter - " "
     */
    fun setFilterName(str: String) {
        filterString = str.split(" ")
    }

    private fun addDevice(device: BluetoothDevice) {
        if (tempListOfDevices.add(device)) {
            _devices.postValue(tempListOfDevices)
        }
    }

    private fun zeroDevices() {
        tempListOfDevices = emptySet<BluetoothDevice>().toMutableSet()
        _devices.postValue(emptySet())
    }

    suspend fun connectToDevice(device: BluetoothDevice, ctx: Context): Boolean {
        return try {
            when (ble.connectToGatt(device, ctx)) {
                true -> {
                    BLEservices = ble.discoverServices()
                    val char = BLEservices.firstOrNull { it.getCharacteristic(uuidCmdChar) != null }
                        ?.getCharacteristic(uuidCmdChar) ?: return false
                    ble.setNotify(char, true)
                    true
                }
                false -> {
                    false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun disconnectFromDevice() {
        try {
            ble.disconnectFromGatt()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun sendCMD(cmd: String): String {
        var char: BluetoothGattCharacteristic? = null

        char = BLEservices.firstOrNull { it.getCharacteristic(uuidCmdChar) != null }
            ?.getCharacteristic(uuidCmdChar)
            ?: return "none"//BluetoothGattCharacteristic(uuidCmdChar,0,0)
        var result: String?
        var allString = ""
        try {
            ble.writeChar(char, cmd)
            while (true) {
                result = ble.waitForChange(char)
                if (!result.contains("\r\n"))
                    result = ble.readChar(char)
                if (result == "" || result == "OK" || result.contains("ER:")) {
                    allString += result
                    break
                }
                ble.writeChar(char, "OK")
                allString += result
            }
        } catch (e: BLEtimeoutException) {
            e.printStackTrace()
            return Strings.get(R.string.timeout_is_over)
        } catch (e: BLEillegalStateException) {
            e.printStackTrace()
            return Strings.get(R.string.unable_to_execute_command)
        }
        return allString
    }
}