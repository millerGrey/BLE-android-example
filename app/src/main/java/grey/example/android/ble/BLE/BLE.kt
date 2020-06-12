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
class BLEtimeoutException() : Exception() {
    override val message: String?
        get() = "Operation's timeout is over"
}

/**
 * Exception when order of operations is wrong
 */
class BLEillegalStateException() : Exception() {
    override val message: String?
        get() = "Gatt server is null. Maybe device is disconnected"
}

/**
 * classes discribed structures for channels
 */
data class ConnectionState(val status: Int, val newState: Int)
data class BluetoothResult(val uuid: UUID, val value: String, val status: Int)
data class OnDescriptorWriteResult(val descriptor: BluetoothGattDescriptor, val status: Int)
data class OnDiscoveredResult(val services: List<BluetoothGattService>)
data class OnChangedResult(val uuid: UUID, val res: String)

/**
 * Class implemented connection and data exchange with BLE device over coroutines.
 */
class BLE : GATToverCoroutines {

    override var btGattServer: BluetoothGatt? = null

    /**
     * List of active devicse's services
     */
    private var GATTservices: List<BluetoothGattService> = emptyList()

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
    private val channelState = Channel<ConnectionState>(Channel.CONFLATED)
    private val channelDiscovery = Channel<OnDiscoveredResult>(Channel.CONFLATED)
    private val channelResult = Channel<BluetoothResult>(Channel.CONFLATED)
    private val channelDescriptor = Channel<OnDescriptorWriteResult>(Channel.CONFLATED)
    private val channelCharacteristicChange = Channel<OnChangedResult>(Channel.CONFLATED)

    /**
     * It is timeout flag for channelCharacteristicChange.
     * If notify will be received after timeout, result of notify will be send into channel.
     * And in next time WaitForChange function return previous result.
     * This flag needed to prevent this situation.
     */
    private var isChannelCharacteristicChangeTimeout = false

    /**
     * Operation's timeout
     */
    private val timeoutMs: Long = 2000

    /**
     *  Implementation of BluetoothGattCallback
     *  In this callback used coroutine channels.
     *  Every collback's function send their parameters in appropriate channel.
     */
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            val r = channelState.offer(ConnectionState(status, newState))
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                btGattServer = null
                gatt.close()
                _isConnected.postValue(false)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            channelDiscovery.offer(OnDiscoveredResult(gatt.services))
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            if (!isChannelCharacteristicChangeTimeout) {
                Log.d("ble_app", characteristic.getStringValue(0))
                channelCharacteristicChange.offer(
                    OnChangedResult(
                        characteristic.uuid,
                        characteristic.getStringValue(0)
                    )
                )
            } else
                isChannelCharacteristicChangeTimeout = false
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.d("ble_app", "onRead " + characteristic.getStringValue(0))
            channelResult.offer(
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
            channelDescriptor.offer(OnDescriptorWriteResult(descriptor, status))
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.d("ble_app", "onWrite " + characteristic.getStringValue(0))
            channelResult.offer(
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
    override suspend fun connectToGatt(device: BluetoothDevice, ctx: Context): Boolean {
        if (!channelState.isEmpty) {
            channelState.receive()
        }
        btGattServer = (device.connectGatt(ctx, false, gattCallback))
        withTimeoutOrNull(timeoutMs) {
            val s = channelState.receive()
            if (s.newState == BluetoothProfile.STATE_CONNECTED) {
                _isConnected.postValue(true)
            } else {
                throw BLEillegalStateException()
            }
        } ?: let {
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
                val s = channelState.receive()
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
    override suspend fun writeChar(char: BluetoothGattCharacteristic, value: String) {
        char.setValue(value)
        channelResult.poll()
        btGattServer?.let {
            it.writeCharacteristic(char)
            withTimeoutOrNull(timeoutMs) {
                val s = channelResult.receive()
            } ?: let {
                throw BLEtimeoutException()
            }
        } ?: let {
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
    override suspend fun readChar(char: BluetoothGattCharacteristic): String {
        lateinit var result: BluetoothResult
        channelResult.poll()
        btGattServer?.let {
            it.readCharacteristic(char)
            withTimeoutOrNull(timeoutMs) {
                result = channelResult.receive()
                Log.d("ble_app", "read ${result.value}")
            } ?: let {
                throw BLEtimeoutException()
            }
            return result.value
        } ?: let {
            throw BLEillegalStateException()
        }
    }

    /**
     *  Implementation of discovering services from BLE device's GATT server over coroutine.
     *  In this function used channel with OnDiscoveredResult class in parameter.
     *  Gatt callback send result in this channel and this function waiting for result from channel.
     *  If timeout is over function throw BLEtimeoutException
     *  If wrong order function throw BLEillegalStateException
     *  @return received list of services
     */
    override suspend fun discoverServices(): List<BluetoothGattService> {
        channelDiscovery.poll()
        btGattServer?.let {
            it.discoverServices()
            withTimeoutOrNull(timeoutMs * 2) {
                GATTservices = channelDiscovery.receive().services
            } ?: let {
                throw BLEtimeoutException()
            }
        } ?: let {
            throw BLEillegalStateException()
        }
        return GATTservices
    }

    /**
     *  Implementation of setting notification for BLE device's characteristic over coroutine.
     *  In this function used channel with OnDescriptorWriteResult class in parameter.
     *  Gatt callback send result in this channel and this function waiting for result from channel.
     *  If timeout is over function throw BLEtimeoutException
     *  If wrong order function throw BLEillegalStateException
     *  @param char - Bluetooth characteristic
     *  @param enable if it true - notification will be enabled
     *  */
    override suspend fun setNotify(char: BluetoothGattCharacteristic, enable: Boolean) {
        channelDescriptor.poll()
        btGattServer?.let {
            it.setCharacteristicNotification(char, enable)
            val descriptor = char.getDescriptor(uiidCCC)?.apply {
                value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }
            val s = it.writeDescriptor(descriptor)
            withTimeoutOrNull(timeoutMs) {
                val s = channelDescriptor.receive()
            } ?: let {
                throw BLEtimeoutException()
            }
        } ?: let {
            throw BLEillegalStateException()
        }
    }


    /**
     *  Implementation of waiting notification from BLE device over coroutine.
     *  In this function used channel with OnChangedResult class in parameter.
     *  Gatt callback send result in this channel and this function waiting for result from channel.
     *  If timeout is over function throw BLEtimeoutException
     *  If wrong order function throw BLEillegalStateException
     *  @param char - Bluetooth characteristic
     *  @return characteristic's string value
     */
    override suspend fun waitForChange(char: BluetoothGattCharacteristic): String {
        lateinit var s: OnChangedResult
        withTimeoutOrNull(timeoutMs) {
            s = channelCharacteristicChange.receive()
        } ?: let {
            isChannelCharacteristicChangeTimeout = true
            throw BLEtimeoutException()
        }
        return s.res
    }
}