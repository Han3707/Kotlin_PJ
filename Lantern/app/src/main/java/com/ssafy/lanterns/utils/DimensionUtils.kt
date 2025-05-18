package com.ssafy.lanterns.utils

import android.content.res.Resources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 픽셀을 dp로 변환합니다.
 */
fun Int.pxToDp(): Dp {
    return (this / Resources.getSystem().displayMetrics.density).dp
}

/**
 * dp를 픽셀로 변환합니다.
 */
fun Int.dpToPx(): Int {
    return (this * Resources.getSystem().displayMetrics.density).toInt()
}

/**
 * 픽셀을 dp로 변환합니다.
 */
fun Float.pxToDp(): Dp {
    return (this / Resources.getSystem().displayMetrics.density).dp
}

/**
 * dp를 픽셀로 변환합니다.
 */
fun Float.dpToPx(): Int {
    return (this * Resources.getSystem().displayMetrics.density).toInt()
}

/**
 * Dp 값을 dp 단위로 변환합니다.
 */
val Number.dp: Dp
    get() = Dp(this.toFloat()) 