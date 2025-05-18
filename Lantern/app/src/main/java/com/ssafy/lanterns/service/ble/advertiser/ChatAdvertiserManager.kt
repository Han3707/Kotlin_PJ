package com.ssafy.lanterns.service.ble.advertiser

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.ssafy.lanterns.config.BleConstants
import com.ssafy.lanterns.config.ChatConstants
import com.ssafy.lanterns.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ChatAdvertiserManager(private val context: Context) {
    private val TAG = "ChatAdvertiser"
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var advertisingJob: Job? = null

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(TAG, "Chat advertising started successfully.")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Chat advertising failed with error code: $errorCode")
            // Consider retry logic or error reporting to ViewModel
        }
    }

    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (bluetoothLeAdvertiser == null) {
            Log.e(TAG, "Bluetooth LE Advertiser not supported.")
        }
    }

    /**
     * Starts BLE advertising with a chat message chunk.
     * The payload structure is:
     * [TYPE(1)] [RECIPIENT_ID(2)] [SENDER_ID(2)] [CHUNK_NUM(1)] [TOTAL_CHUNKS(1)] [MESSAGE_ID(8)] [CHUNK_DATA(variable)]
     */
    fun startAdvertising(
        messageType: Byte = ChatConstants.MESSAGE_TYPE_DM_CHUNK,
        recipientId: Short,
        senderId: Short,
        chunkNumber: Byte,
        totalChunks: Byte,
        messageId: Long,
        payloadChunk: ByteArray
    ) {
        if (bluetoothLeAdvertiser == null) {
            Log.e(TAG, "Advertiser not initialized or not supported.")
            return
        }

        if (payloadChunk.size > ChatConstants.MAX_CHAT_MESSAGE_CHUNK_LENGTH) {
            Log.e(TAG, "Payload chunk too large: ${payloadChunk.size} bytes. Max is ${ChatConstants.MAX_CHAT_MESSAGE_CHUNK_LENGTH}")
            return
        }

        advertisingJob?.cancel() // Cancel previous short-lived advertising job

        val bufferSize = ChatConstants.PAYLOAD_HEADER_SIZE + payloadChunk.size
        if (bufferSize > ChatConstants.MAX_MANUFACTURER_DATA_PAYLOAD_SIZE) {
             Log.e(TAG, "Total payload size too large: $bufferSize bytes. Max is ${ChatConstants.MAX_MANUFACTURER_DATA_PAYLOAD_SIZE}")
            return
        }

        val manufacturerDataPayload = ByteBuffer.allocate(bufferSize).apply {
            order(ByteOrder.BIG_ENDIAN) // Consistent with scanner
            put(messageType)
            putShort(recipientId)
            putShort(senderId)
            put(chunkNumber)
            put(totalChunks)
            putLong(messageId)
            put(payloadChunk)
        }.array()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0) // We will manage timeout manually for short bursts
            .build()

        val data = AdvertiseData.Builder()
            .addManufacturerData(BleConstants.MANUFACTURER_ID_CHAT, manufacturerDataPayload)
            // Not including service UUID in advertisement packet to save space for manufacturer data
            // .addServiceUuid(ParcelUuid(BleConstants.CHAT_SERVICE_UUID))
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        try {
            // Start new advertising
            bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
            Log.d(TAG, "Chat advertising attempt. Payload size: ${manufacturerDataPayload.size}, Chunk: ${chunkNumber + 1}/$totalChunks, MsgID: $messageId")

            // Automatically stop advertising after a short period (e.g., 500ms per chunk)
            // This helps in sending multiple chunks without them interfering too much
            // and allows the device to switch to scanning more quickly if needed.
            advertisingJob = CoroutineScope(Dispatchers.IO).launch {
                delay(500) // Short advertising duration for each chunk
                stopAdvertisingInternal() // Internal stop without cancelling job, as job is self-terminating
                Log.d(TAG, "Chat advertising for chunk ${chunkNumber + 1} automatically stopped.")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Bluetooth permission missing for advertising: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Could not start chat advertising: ${e.message}")
        }
    }

    private fun stopAdvertisingInternal() {
        try {
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            // Log.d(TAG, "Chat advertising stopped (internal).")
        } catch (e: SecurityException) {
            Log.e(TAG, "Bluetooth permission missing for stopping advertising (internal): ${e.message}")
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Could not stop chat advertising (internal), not started or already stopped: ${e.message}")
        }
    }

    // Public method to be called from ViewModel's onCleared or when chat screen is left
    fun stopAdvertising() {
        advertisingJob?.cancel()
        advertisingJob = null
        stopAdvertisingInternal()
        Log.d(TAG, "Chat advertising explicitly stopped.")
    }
    
    // Call this if advertiser might have been started but not explicitly stopped by the short-duration job
    fun stopAdvertisingIfNeverStopped() {
        if (advertisingJob != null && advertisingJob?.isActive == true) {
            Log.w(TAG, "Chat advertising job was still active. Stopping now.")
            stopAdvertising()
        } else if (advertisingJob == null) {
            // If there's no job, it means either it was never started, completed, or explicitly stopped.
            // A safety stop call might be good if there's a chance an advertisement was started without a job.
            // However, current design ties all starts to a job.
        }
    }
} 