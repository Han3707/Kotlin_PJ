package com.ssafy.lantern.data.source.ble.gatt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattDescriptor
import java.util.UUID

class GattServerManager (
    private val context: Context,
    var onConnectionStateChange: (device: BluetoothDevice, status: Int, newState: Int) -> Unit,
    var onClientSubscribed: (device: BluetoothDevice) -> Unit,
    var onClientUnsubscribed: (device: BluetoothDevice) -> Unit
) {
    private var gattServer: BluetoothGattServer? = null
    private val connectedClients = mutableMapOf<String, BluetoothDevice>()
    private var characteristic: BluetoothGattCharacteristic? = null
    private var isServerOpen = false

    @SuppressLint("MissingPermission")
    fun openGattServer(){
        if (isServerOpen) {
            Log.d("GattServerManager", "GATT Server is already open.")
            return
        }
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        if (bluetoothManager == null) {
            Log.e("GattServerManager", "BluetoothManager not found.")
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            try {
                gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
                addChatService()
                isServerOpen = true
                Log.i("GattServerManager", "GATT Server opened successfully.")
            } catch (e: Exception) {
                Log.e("GattServerManager", "Failed to open GATT server", e)
                isServerOpen = false
            }
        } else {
            Log.e("GattServerManager", "Permission denied: BLUETOOTH_CONNECT. Cannot open GATT server.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun addChatService() {
        if (gattServer == null) {
             Log.e("GattServerManager", "GATT Server is null, cannot add service.")
             return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e("GattServerManager", "Permission denied: BLUETOOTH_CONNECT. Cannot add service.")
            return
        }

        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        characteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val cccDescriptor = BluetoothGattDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        characteristic?.addDescriptor(cccDescriptor)

        service.addCharacteristic(characteristic)

        try {
            gattServer?.addService(service)
            Log.i("GattServerManager", "Chat service added successfully.")
        } catch (e: Exception) {
             Log.e("GattServerManager", "Failed to add chat service", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun closeGattServer() {
        if (!isServerOpen || gattServer == null) return
         if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            try {
                gattServer?.close()
                Log.i("GattServerManager", "GATT Server closed.")
            } catch (e: Exception) {
                 Log.e("GattServerManager", "Error closing GATT server", e)
            }
         } else {
              Log.e("GattServerManager", "Permission denied: BLUETOOTH_CONNECT. Cannot close server.")
         }
        gattServer = null
        isServerOpen = false
        connectedClients.clear()
    }

    private val gattServerCallback = object : BluetoothGattServerCallback(){
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int){
            Log.d("GattServerManager", "onConnectionStateChange: Device ${device.address}, Status $status, NewState $newState")
            this@GattServerManager.onConnectionStateChange(device, status, newState)

            if(newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("GattServerManager", "Device connected: ${device.address}")
                connectedClients[device.address] = device
            } else if(newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("GattServerManager", "Device disconnected: ${device.address}")
                connectedClients.remove(device.address)
                onClientUnsubscribed(device)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            if (descriptor.uuid == UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")) {
                val status = if (value != null && value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    Log.i("GattServerManager", "Client ${device.address} subscribed to notifications")
                    onClientSubscribed(device)
                    BluetoothGatt.GATT_SUCCESS
                } else if (value != null && value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                     Log.i("GattServerManager", "Client ${device.address} unsubscribed from notifications")
                     onClientUnsubscribed(device)
                     BluetoothGatt.GATT_SUCCESS
                } else {
                    Log.w("GattServerManager", "Invalid descriptor write request from ${device.address}")
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH
                }

                if (responseNeeded) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        gattServer?.sendResponse(device, requestId, status, offset, value)
                    } else {
                         Log.e("GattServerManager", "Permission denied: BLUETOOTH_CONNECT for sendResponse")
                    }
                }
            } else {
                Log.w("GattServerManager", "Unknown descriptor write request UUID: ${descriptor.uuid}")
                if (responseNeeded) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, value)
                    } else {
                         Log.e("GattServerManager", "Permission denied: BLUETOOTH_CONNECT for sendResponse")
                    }
                }
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) { /* ... */ }
        override fun onCharacteristicReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic?) { /* ... */ }
        override fun onCharacteristicWriteRequest(device: BluetoothDevice?, requestId: Int, characteristic: BluetoothGattCharacteristic?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) { /* ... */ }
        override fun onNotificationSent(device: BluetoothDevice?, status: Int) { /* ... */ }
        override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) { /* ... */ }
    }

    @SuppressLint("MissingPermission")
    fun broadcastMessage(message: String){
        if (!isServerOpen || gattServer == null || characteristic == null) {
             Log.w("GattServerManager", "Server not ready or characteristic not set.")
             return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e("GattServerManager", "Permission denied: BLUETOOTH_CONNECT. Cannot broadcast message.")
            return
        }

        val messageBytes = message.toByteArray(Charsets.UTF_8)
        characteristic?.value = messageBytes
        val subscribedClients = connectedClients.values

        Log.d("GattServerManager", "Broadcasting message '$message' to ${subscribedClients.size} clients.")
        for (device in subscribedClients) {
            try {
                val success = gattServer?.notifyCharacteristicChanged(device, characteristic!!, false)
                 Log.d("GattServerManager", "Notifying ${device.address}: ${if(success == true) "Success" else "Fail"}")
            } catch (e: Exception) {
                Log.e("GattServerManager", "Error notifying client ${device.address}", e)
            }
        }
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("00001234-0000-1000-8000-00805f9b34fb")
    }
}