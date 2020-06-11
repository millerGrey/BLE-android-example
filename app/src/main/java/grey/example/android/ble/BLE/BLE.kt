package c.grey.gardbt.BLE

import android.bluetooth.*
import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import java.util.*


/**
 * Exception when operation's timeout is over
 */
class BLEtimeoutException(): Exception() {
    override val message: String?
        get() = "Operation's timeout is over"
}

/**
 * Exception when order of operations is wrong
 */
class BLEillegalStateException(): Exception() {
    override val message: String?
        get() = "Gatt server is null. Maybe device is disconnected"
}

/**
 * classes discribed structures for channels
 */
data class ConnectionState(val status: Int, val newState: Int)
data class BluetoothResult(val uuid: UUID, val value: String, val status: Int)
data class OnDescriptorWrite(val descriptor: BluetoothGattDescriptor, val status: Int)
data class OnDiscovered(val services: List<BluetoothGattService>)
data class OnChanged(val uuid: UUID, val res: String)

/**
 * Class implemented connection and data exchange with BLE device over coroutines.
 */
class BLE: GATToverCoroutines {

    override var btGattServer: BluetoothGatt? = null

    /**
     * List of active devicse's services
     */
    private var btServices: List<BluetoothGattService> = emptyList()

    /**
     * Boolean LiveData parameter to notify about change connection state
     */
    private var _isConnected = MutableLiveData<Boolean>(false)
    override val isConnected: LiveData<Boolean>
        get() = _isConnected
    /**
     * Client Characteristic Configuration Descriptor uuid
     */
    private val uiidCCC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /**
     * Coroutine channels for interaction with BLE device
     */
    private val chState = Channel<ConnectionState>(Channel.CONFLATED)
    private val chDisco = Channel<OnDiscovered>(Channel.CONFLATED)
    private val chResult = Channel<BluetoothResult>(Channel.CONFLATED)
    private val chDesc = Channel<OnDescriptorWrite>(Channel.CONFLATED)
    private val chChange = Channel<OnChanged>(Channel.CONFLATED)

    private var isChChangeTimeout = false //TODO описать

    /**
     * Operation's timeout
     */
    private val timeoutMs: Long = 2000


    /**
     *  Implementation of BluetoothGattCallback
     *  In this callback used coroutine channels.
     *  Every collback's function send their parameters in appropriate channel.
     */
    private val gattCallback = object: BluetoothGattCallback(){

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            val r = chState.offer(ConnectionState(status, newState))
            if(newState == BluetoothProfile.STATE_DISCONNECTED){
                btGattServer = null
                gatt.close()
                _isConnected.postValue(false)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
           chDisco.offer(OnDiscovered(gatt.services))
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicChanged(gatt, characteristic)
            Log.d("ARD","1 $isChChangeTimeout")
            if(!isChChangeTimeout){
                Log.d("ARD",characteristic.getStringValue(0))
                chChange.offer(OnChanged(characteristic.uuid,  characteristic.getStringValue(0)))}
            else
                isChChangeTimeout = false
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ){
            Log.d("ARD","onRead " + characteristic.getStringValue(0))
            chResult.offer(
                BluetoothResult(
                    characteristic.uuid,
                    characteristic.getStringValue(0) ?: "null",
                    status
                )
            )
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            chDesc.offer(OnDescriptorWrite(descriptor, status))
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.d("ARD","onWrite " + characteristic.getStringValue(0))
            chResult.offer(
                BluetoothResult(
                    characteristic.uuid,
                    characteristic.getStringValue(0) ?: "null",
                    status
                )
            )
        }
    }

    /**
     *  Implementation of to connecting to GATT server over coroutine.
     *  In this function used channel with ConnectionState class in parameter.
     *  Gatt callback send result in this channel and this function waiting for result from channel.
     *  @param device  - device to connect
     *  @param ctx - context
     *  @return true if device connected or throw exception
     */
    @ExperimentalCoroutinesApi
    override suspend fun connectToGatt(device: BluetoothDevice, ctx: Context):Boolean{
        if(!chState.isEmpty){
            chState.receive()
        }
        btGattServer = (device.connectGatt(ctx, false, gattCallback))
        withTimeoutOrNull(timeoutMs){
            val s = chState.receive()
            if(s.newState == BluetoothProfile.STATE_CONNECTED){
                _isConnected.postValue(true)
            }else{
                throw BLEillegalStateException()
            }
        }?:let{
            throw BLEtimeoutException()
        }
        return true
    }

    /**
     *  Implementation of disconnecting from GATT server over coroutine.
     *  In this function used channel with ConnectionState class in parameter.
     *  Gatt callback send result in this channel and this function waiting for result from channel.
     *  If timeout is over function throw BLEtimeoutException
     *  If wrong order function throw BLEillegalStateException
     */
    override suspend fun disconnectFromGatt() {
        btGattServer?.let {
            it.disconnect()
            withTimeoutOrNull(timeoutMs) {
                val s = chState.receive()
                if (s.newState == BluetoothProfile.STATE_DISCONNECTED) {
                } else {
                    throw BLEillegalStateException()
                }
            } ?: let {
                throw BLEtimeoutException()
            }
        }
    }

    /**
     *  Implementation of writing characteristic to BLE device over coroutine.
     *  In this function used channel with BluetoothResult class in parameter.
     *  Gatt callback send result in this channel and this function waiting for result from channel.
     *  If timeout is over function throw BLEtimeoutException
     *  If wrong order function throw BLEillegalStateException
     *  @param char - Bluetooth Characteristic
     *  @param value - writing value
     */
    override suspend  fun writeChar(char: BluetoothGattCharacteristic, value: String){
        char.setValue(value)
        btGattServer?.let{
            it.writeCharacteristic(char)
            withTimeoutOrNull(timeoutMs){
                val s = chResult.receive()
            }?:let{
                throw BLEtimeoutException()
            }
        }?:let{
            throw BLEillegalStateException()
        }
    }

    /**
     *  Implementation of reading characteristic's string value from BLE device over coroutine.
     *  In this function used channel with BluetoothResult class in parameter.
     *  Gatt callback send result in this channel and this function waiting for result from channel.
     *  If timeout is over function throw BLEtimeoutException
     *  If wrong order function throw BLEillegalStateException
     *  @param char - Bluetooth Characteristic
     *  @return received characteristic's string value
     */
    override suspend  fun readChar(char: BluetoothGattCharacteristic): String{
        lateinit var result: BluetoothResult
        btGattServer?.let{
            it.readCharacteristic(char)
            withTimeoutOrNull(timeoutMs){
                result = chResult.receive()
                Log.d("ARD","read ${result.value}")
            }?:let{
                throw BLEtimeoutException()
            }
            return result.value
        }?:let{
            throw BLEillegalStateException()
        }
    }

    /**
     *  Implementation of discovering services from BLE device's GATT server over coroutine.
     *  In this function used channel with OnDiscovered class in parameter.
     *  Gatt callback send result in this channel and this function waiting for result from channel.
     *  If timeout is over function throw BLEtimeoutException
     *  If wrong order function throw BLEillegalStateException
     *  @return received list of services
     */
    override suspend fun discoverServices(): List<BluetoothGattService> {
        btGattServer?.let{
            it.discoverServices()
            withTimeoutOrNull(timeoutMs * 2) {
                btServices = chDisco.receive().services
            }?:let{
                throw BLEtimeoutException()
            }
        }?:let{
            throw BLEillegalStateException()
        }
        return btServices
    }

    /**
     *  Implementation of setting notification for BLE device's characteristic over coroutine.
     *  In this function used channel with OnDescriptorWrite class in parameter.
     *  Gatt callback send result in this channel and this function waiting for result from channel.
     *  If timeout is over function throw BLEtimeoutException
     *  If wrong order function throw BLEillegalStateException
     *  @param char - Bluetooth characteristic
     *  @param enable if it true - notification will be enabled
     *  */
    override suspend fun setNotify(char: BluetoothGattCharacteristic, enable: Boolean){
        btGattServer?.let{
            it.setCharacteristicNotification(char, enable)
            val descriptor = char.getDescriptor(uiidCCC)?.apply {
                value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }
            val s = it.writeDescriptor(descriptor)
            withTimeoutOrNull(timeoutMs){
                val s = chDesc.receive()
            }?:let{
                throw BLEtimeoutException()
            }
        }?:let{
            throw BLEillegalStateException()
        }
    }


    /**
     *  Implementation of waiting notification from BLE device over coroutine.
     *  In this function used channel with OnChanged class in parameter.
     *  Gatt callback send result in this channel and this function waiting for result from channel.
     *  If timeout is over function throw BLEtimeoutException
     *  If wrong order function throw BLEillegalStateException
     *  @param char - Bluetooth characteristic
     *  @return characteristic's string value
     */
    override suspend fun waitForChange(char: BluetoothGattCharacteristic): String{
        lateinit var s: OnChanged
        isChChangeTimeout = false
        withTimeoutOrNull(timeoutMs){
            s = chChange.receive()
        }?:let{
            Log.d("ARD","set true")
            isChChangeTimeout = true
            throw BLEtimeoutException()
        }
        return s.res
    }
}