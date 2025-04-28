package com.example.bletest.data.model

import android.bluetooth.BluetoothDevice
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 메시지 데이터를 표현하는 클래스
 */
@Parcelize
data class MessageData(
    val message: ParsedMessage,
    val rawData: ByteArray? = null,
    val device: BluetoothDevice? = null,
    val isOutgoing: Boolean = false
) : Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MessageData

        if (message != other.message) return false
        if (rawData != null) {
            if (other.rawData == null) return false
            if (!rawData.contentEquals(other.rawData)) return false
        } else if (other.rawData != null) return false
        if (device != other.device) return false
        if (isOutgoing != other.isOutgoing) return false

        return true
    }

    override fun hashCode(): Int {
        var result = message.hashCode()
        result = 31 * result + (rawData?.contentHashCode() ?: 0)
        result = 31 * result + (device?.hashCode() ?: 0)
        result = 31 * result + isOutgoing.hashCode()
        return result
    }
}

// 추후 메시지 데이터 모델 클래스가 위치할 파일입니다. 