package com.example.bletest.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * 파싱된 메시지 데이터를 표현하는 클래스
 */
@Parcelize
data class ParsedMessage(
    val messageId: String? = null,
    val sourceId: String? = null,
    val targetId: String? = null,
    val messageType: MessageType = MessageType.UNKNOWN,
    val content: String? = null,
    val ttl: Int = 0,
    val protocolVersion: Int = 0,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable 