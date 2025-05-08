package com.ssafy.lantern.data.model

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * 전송 계층 헤더 구조
 * - 데이터 크기를 최소화하기 위해 최적화된 헤더 구조
 */
data class TransportHeader(
    // 시퀀스 번호 (Long -> Int로 변경, 4바이트)
    val seqNum: Int,
    
    // 세그먼트 인덱스 (1바이트 - 최대 255개 세그먼트 지원)
    val segmentIndex: Int,
    
    // 전체 세그먼트 수 (1바이트 - 최대 255개 세그먼트 지원)
    val totalSegments: Int
) {
    companion object {
        // 헤더 크기: 4(Int) + 1(Byte) + 1(Byte) = 6 바이트
        const val HEADER_SIZE = 6
        
        /**
         * 바이트 배열에서 헤더 파싱
         */
        fun fromBytes(bytes: ByteArray): TransportHeader? {
            if (bytes.size < HEADER_SIZE) return null
            
            try {
                ByteArrayInputStream(bytes).use { byteStream ->
                    DataInputStream(byteStream).use { dataStream ->
                        val seqNum = dataStream.readInt()
                        val segmentIndex = dataStream.readByte().toInt() and 0xFF
                        val totalSegments = dataStream.readByte().toInt() and 0xFF
                        
                        return TransportHeader(seqNum, segmentIndex, totalSegments)
                    }
                }
            } catch (e: Exception) {
                return null
            }
        }
    }
    
    /**
     * 헤더를 바이트 배열로 직렬화
     */
    fun toBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { dataStream ->
            dataStream.writeInt(seqNum)
            dataStream.writeByte(segmentIndex)
            dataStream.writeByte(totalSegments)
        }
        
        return output.toByteArray()
    }
} 