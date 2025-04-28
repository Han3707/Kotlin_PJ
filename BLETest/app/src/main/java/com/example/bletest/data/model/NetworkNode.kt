package com.example.bletest.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 네트워크 노드 정보를 표현하는 클래스
 */
@Parcelize
data class NetworkNode(
    val address: String,
    val deviceId: String,
    val lastSeen: Long = System.currentTimeMillis(),
    val hopCount: Int = 0,
    val rssi: Int = 0
) : Parcelable 