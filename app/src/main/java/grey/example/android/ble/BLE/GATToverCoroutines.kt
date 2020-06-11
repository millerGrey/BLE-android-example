package c.grey.gardbt.BLE

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import androidx.lifecycle.LiveData
import java.util.*

/**
 *  Interface to interaction with BLE device over coroutines
 */
interface GATToverCoroutines {

    /**
     *  Instance of BLE devices's GATT server
     */
    var btGattServer: BluetoothGatt?

    /**
     *  Boolean LiveData parameter to notify about change connection state
     */
    val isConnected: LiveData<Boolean>

    /**
     *  Connecting to BLE device's GATT server over coroutine.
     */
    suspend fun connectToGatt(device: BluetoothDevice, ctx: Context):Boolean

    /**
     *  Disconnecting from BLE device's GATT server over coroutine.
     */
    suspend fun disconnectFromGatt()

    /**
     *  Discovering services from BLE device's GATT server over coroutine.
     */
    suspend fun discoverServices(): List<BluetoothGattService>

    /**
     *  Setting notification for BLE device's characteristic over coroutine.
     */
    suspend fun setNotify(char: BluetoothGattCharacteristic, enable: Boolean)

    /**
     *  Writing characteristic to BLE device over coroutine.
     */
    suspend fun writeChar(char: BluetoothGattCharacteristic, value: String)

    /**
     *  Reading characteristic's string value from BLE device over coroutine.
     */
    suspend fun readChar(char: BluetoothGattCharacteristic): String

    /**
     *   Waiting notification from BLE device over coroutine.
     */
    suspend fun waitForChange(char: BluetoothGattCharacteristic): String
}
