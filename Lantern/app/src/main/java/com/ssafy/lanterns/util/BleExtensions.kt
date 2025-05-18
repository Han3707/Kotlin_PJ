package com.ssafy.lanterns.util

/**
 * Converts a Long to a Short by taking the lower 16 bits.
 * This is a lossy conversion if the Long value is outside the Short range.
 */
fun Long.toShort(): Short {
    return (this and 0xFFFFL).toShort() // 하위 16비트만 취하고 Short로 변환
}

/**
 * Converts a Short to a Long, preserving the sign if it was negative.
 */
fun Short.toLongExt(): Long {
    return this.toLong() and 0xFFFFL // unsigned short to long
    // 또는 그냥 this.toLong() nếu signed short 가 필요하다면.
    // 여기서는 User ID가 양수라고 가정하고, toShort()에서 잘린 부분을 복원할 목적은 아님.
} 