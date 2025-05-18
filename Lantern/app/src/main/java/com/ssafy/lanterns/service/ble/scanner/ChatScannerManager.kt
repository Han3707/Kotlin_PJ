package com.ssafy.lanterns.service.ble.scanner

import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.os.Looper
import android.util.Log
import com.ssafy.lanterns.config.BleConstants
import com.ssafy.lanterns.config.ChatConstants
import com.ssafy.lanterns.util.toLongExt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ParsedChatMessage(
    val messageType: Byte,
    val recipientId: Long, // Renamed from targetUserId for clarity
    val senderId: Long,    // Renamed from senderUserId for clarity
    val chunkNumber: Byte, // Renamed from chunkIndex for clarity
    val totalChunks: Byte,
    val messageId: Long,   // Unique ID for the message (e.g., timestamp)
    val chunkData: ByteArray // Renamed from messageContent for clarity
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ParsedChatMessage

        if (messageType != other.messageType) return false
        if (recipientId != other.recipientId) return false
        if (senderId != other.senderId) return false
        if (chunkNumber != other.chunkNumber) return false
        if (totalChunks != other.totalChunks) return false
        if (messageId != other.messageId) return false
        if (!chunkData.contentEquals(other.chunkData)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = messageType.toInt()
        result = 31 * result + recipientId.hashCode()
        result = 31 * result + senderId.hashCode()
        result = 31 * result + chunkNumber.toInt()
        result = 31 * result + totalChunks.toInt()
        result = 31 * result + messageId.hashCode()
        result = 31 * result + chunkData.contentHashCode()
        return result
    }
}

class ChatScannerManager(private val context: Context) {
    private val TAG = "ChatScanner"
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var currentUserId: Long? = null

    // (SenderId, MessageId, ChunkNumber) to filter duplicate packets
    private val recentPackets = mutableSetOf<Triple<Short, Long, Byte>>()
    private val packetCleanupDelay = 5000L // 5 seconds to clear from set

    private val _chatMessageReceived = MutableSharedFlow<ParsedChatMessage>(replay = 0)
    val chatMessageReceived: SharedFlow<ParsedChatMessage> = _chatMessageReceived.asSharedFlow()

    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        bluetoothLeScanner = adapter?.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "Bluetooth LE Scanner not supported.")
        }
    }

    fun setCurrentUserId(userId: Long) {
        this.currentUserId = userId
        Log.d(TAG, "Current user ID set to: $userId")
    }

    fun startScanning() { // Removed onMessageReceived callback, using SharedFlow instead
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "Scanner not initialized or not supported.")
            return
        }
        if (currentUserId == null) {
            Log.e(TAG, "Current User ID not set. Cannot filter messages effectively.")
            // Allow scanning but log warning, filtering might not be perfect if currentUserId is set late.
        }

        stopScanning() // Ensure any previous scan is stopped

        val scanFilters = listOf(
            ScanFilter.Builder()
                .setManufacturerData(BleConstants.MANUFACTURER_ID_CHAT, null, null)
                .build()
        )

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // Use low latency for chat
            .setReportDelay(0) // Report results immediately
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.scanRecord?.getManufacturerSpecificData(BleConstants.MANUFACTURER_ID_CHAT)?.let {
                    parseAndProcessPayload(it, result.device.address)
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { result ->
                    result.scanRecord?.getManufacturerSpecificData(BleConstants.MANUFACTURER_ID_CHAT)?.let {
                        parseAndProcessPayload(it, result.device.address)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Chat scan failed with error code: $errorCode")
            }
        }

        try {
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
            Log.d(TAG, "Chat scanning started for manufacturer ID: ${BleConstants.MANUFACTURER_ID_CHAT}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Bluetooth permission missing for scanning: ${e.message}")
        }
    }

    private fun parseAndProcessPayload(payload: ByteArray, deviceAddress: String) {
        try {
            if (payload.size < ChatConstants.PAYLOAD_HEADER_SIZE) {
                Log.w(TAG, "[$deviceAddress] Received payload too short for header: ${payload.size} bytes. Expected at least ${ChatConstants.PAYLOAD_HEADER_SIZE}")
                return
            }

            val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)

            val messageType = buffer.get(ChatConstants.PAYLOAD_OFFSET_TYPE)
            val recipientIdShort = buffer.getShort(ChatConstants.PAYLOAD_OFFSET_RECIPIENT_ID)
            val senderIdShort = buffer.getShort(ChatConstants.PAYLOAD_OFFSET_SENDER_ID)
            val chunkNumber = buffer.get(ChatConstants.PAYLOAD_OFFSET_CHUNK_NUMBER)
            val totalChunks = buffer.get(ChatConstants.PAYLOAD_OFFSET_TOTAL_CHUNKS)
            val messageId = buffer.getLong(ChatConstants.PAYLOAD_OFFSET_MESSAGE_ID)

            // Duplicate packet filtering based on (SenderId, MessageId, ChunkNumber)
            val packetKey = Triple(senderIdShort, messageId, chunkNumber)
            synchronized(recentPackets) {
                if (recentPackets.contains(packetKey)) {
                    // Log.v(TAG, "[$deviceAddress] Duplicate packet filtered: $packetKey")
                    return
                }
                recentPackets.add(packetKey)
                android.os.Handler(Looper.getMainLooper()).postDelayed({
                    synchronized(recentPackets) {
                        recentPackets.remove(packetKey)
                    }
                }, packetCleanupDelay)
            }

            val localCurrentUserId = currentUserId
            if (messageType == ChatConstants.MESSAGE_TYPE_DM_CHUNK) {
                if (localCurrentUserId == null || recipientIdShort.toLongExt() != localCurrentUserId) {
                    // Log.v(TAG, "[$deviceAddress] DM chunk not for me or user ID not set. Target: ${recipientIdShort.toLongExt()}, Me: $localCurrentUserId, Sender: ${senderIdShort.toLongExt()}, MsgID: $messageId")
                    return
                }
            } else {
                Log.w(TAG, "[$deviceAddress] Received unknown or unsupported message type: $messageType")
                return
            }
            
            val chunkDataSize = payload.size - ChatConstants.PAYLOAD_HEADER_SIZE
            if (chunkDataSize < 0) {
                 Log.e(TAG, "[$deviceAddress] Invalid chunk data size: $chunkDataSize. Payload size: ${payload.size}")
                return
            }
            if (chunkDataSize > ChatConstants.MAX_CHAT_MESSAGE_CHUNK_LENGTH) {
                 Log.w(TAG, "[$deviceAddress] Received chunk data size ($chunkDataSize) exceeds max (${ChatConstants.MAX_CHAT_MESSAGE_CHUNK_LENGTH}). MsgID: $messageId. Truncating or ignoring.")
                // Potentially handle this case, e.g. by taking only MAX_CHAT_MESSAGE_CHUNK_LENGTH bytes
            }

            val chunkData = ByteArray(chunkDataSize)
            buffer.position(ChatConstants.PAYLOAD_HEADER_SIZE)
            buffer.get(chunkData)

            val parsedMessage = ParsedChatMessage(
                messageType = messageType,
                recipientId = recipientIdShort.toLongExt(),
                senderId = senderIdShort.toLongExt(),
                chunkNumber = chunkNumber,
                totalChunks = totalChunks,
                messageId = messageId,
                chunkData = chunkData
            )
            
            // Log.d(TAG, "[$deviceAddress] Chat message chunk parsed: Sender: ${parsedMessage.senderId}, MsgID: ${parsedMessage.messageId}, Chunk: ${parsedMessage.chunkNumber + 1}/${parsedMessage.totalChunks}, Size: ${chunkData.size}")
            val emitted = _chatMessageReceived.tryEmit(parsedMessage)
            if (!emitted) {
                Log.w(TAG, "[$deviceAddress] Failed to emit chat message to flow. Buffer overflow? MsgID: $messageId")
            }

        } catch (e: Exception) {
            Log.e(TAG, "[$deviceAddress] Error parsing chat payload: ${e.message}\nPayload: ${payload.joinToString { String.format("%02X", it) }}", e)
        }
    }

    fun stopScanning() {
        try {
            scanCallback?.let {
                bluetoothLeScanner?.stopScan(it)
                scanCallback = null // Important to release callback
                Log.d(TAG, "Chat scanning stopped.")
            }
            // Clear recent packets when stopping scan to free memory and allow fresh packets on next start
            synchronized(recentPackets) {
                recentPackets.clear()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Bluetooth permission missing for stopping scan: ${e.message}")
        } catch (e: IllegalStateException) {
            // Can happen if BT is off
            Log.w(TAG, "Could not stop chat scanning, not started or BT off: ${e.message}")
        }
    }
} 