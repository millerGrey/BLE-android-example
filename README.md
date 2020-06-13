# BLE-android-example
Bluetooth Low Energy(BLE) android example with using kotlin coroutines
 
Simple android application for interaction with BLE devices.
Work with Bluetooth in android implements with callbacks by default. 
To facilitate work with callbacks I used kotlin coroutines in this app.
All functions to interation with device are suspend. 
It descrybed in GATToverCoroutines interface and imblemented in BLE class.
Data exchange with BLE device based on this functions.

# Demonstration
You need a ESP32 module for demonstration this example.
You also need load an arduino sketch in module. It is in folder [Arduino sketch for ESP32](https://github.com/millerGrey/BLE-android-example/tree/master/Arduino%20sketch%20for%20ESP32).


After that:
- Press the search button
- Press the item with name of your device
- Wait for device connecting
- Send command to device (see below)

Device supports command **get**.
If you send this command from application, device replies with 10 example strings.
If you want, you can implement supporting of your own commands.

