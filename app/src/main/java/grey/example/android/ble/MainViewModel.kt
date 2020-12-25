package grey.example.android.ble

import android.app.Application
import android.bluetooth.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import grey.example.android.ble.ble.BLE
import grey.example.android.ble.ble.BLEprocessing
import kotlinx.coroutines.*

class MainViewModel(application: Application) : AndroidViewModel(application){

    private var _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String>
        get() = _toastMessage

    private var _spinner = MutableLiveData<Boolean>(false)
    val spinner: LiveData<Boolean>
        get() = _spinner

    val isScan: LiveData<Boolean>
        get() = processing.isScan

    val isConnected: LiveData<Boolean>
        get() = processing.ble.isConnected

    val devices: LiveData<Set<BluetoothDevice>>
        get() =  processing.devices

    private var activeDevice: BluetoothDevice? =null

    private var _result = MutableLiveData<String>()
    val result: LiveData<String>
        get() = _result

    val processing = BLEprocessing(BLE())

    init{
        /**
         * enter name of your devices or delete this string
         */
        processing.setFilterName("MyESP32")
    }

    fun reset(){
        _result.value = ""
        activeDevice = null
    }
    fun startScan(){
        processing.startScan()
    }

    fun stopScan(){
        processing.stopScan()
    }

    fun disconnect(){
        activeDevice = null
        viewModelScope.launch {
            processing.disconnectFromDevice()
        }
//        _toastMessage.value = getApplication<Application>().getString(R.string.disconnected)
        _result.value = ""
    }


    fun sendCommand(str: String){
        if(spinner.value == true){
            return
        }
        _spinner.value = true
        viewModelScope.launch{
            val result = processing.sendCMD(str)

            when(result){
                "ER: Wrong data"->_result.value = getApplication<Application>().getString(R.string.wrong_data)
                "ER: No support command"->_result.value = getApplication<Application>().getString(R.string.command_not_support)
                else -> _result.value = result
            }
            _spinner.value = false
        }
    }



    fun onItemClick(pos: Int){
        stopScan()
        activeDevice  = devices.value?.toList()?.get(pos)
        if(isConnected.value == false){
            viewModelScope.launch {
                activeDevice?.let {
                    if (processing.connectToDevice(it, getApplication())) {
                        _toastMessage.value = String.format(getApplication<Application>().getString(R.string.connected),it.name.toString())
                    }
                }
            }
        }else {
            disconnect()
        }
    }
}