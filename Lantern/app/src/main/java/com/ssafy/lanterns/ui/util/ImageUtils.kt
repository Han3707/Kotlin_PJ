package com.ssafy.lanterns.ui.util

import androidx.annotation.DrawableRes
import com.ssafy.lanterns.R

// 이미지 번호(1-15)를 직접 받아 해당 Drawable Resource ID를 반환합니다.
// 유효하지 않은 번호가 입력될 경우 기본 이미지를 반환합니다.
@DrawableRes
fun getProfileImageByNumber(imageNumber: Int): Int {
    return when (imageNumber) {
        1 -> R.drawable.profile_1
        2 -> R.drawable.profile_2
        3 -> R.drawable.profile_3
        4 -> R.drawable.profile_4
        5 -> R.drawable.profile_5
        6 -> R.drawable.profile_6
        7 -> R.drawable.profile_7
        8 -> R.drawable.profile_8
        9 -> R.drawable.profile_9
        10 -> R.drawable.profile_10
        11 -> R.drawable.profile_11
        12 -> R.drawable.profile_12
        13 -> R.drawable.profile_13
        14 -> R.drawable.profile_14
        15 -> R.drawable.profile_15
        else -> R.drawable.lantern_image // 기본 또는 fallback 이미지
    }
}

// 사용자가 프로필 이미지를 직접 선택하지 않았을 때 사용될 기본 프로필 이미지 "번호"를 반환합니다.
// 모든 신규 또는 미설정 사용자는 1번 프로필 이미지를 갖게 됩니다.
fun getDefaultProfileImageNumber(): Int {
    return 1
}

// 프로필 이미지 선택 UI 등에 사용될 수 있도록
// 모든 프로필 이미지의 번호와 리소스 ID 쌍을 Map 형태로 반환합니다.
fun getAllProfileImageResources(): Map<Int, Int> {
    return (1..15).associateWith { getProfileImageByNumber(it) }
}

// 기존 getProfileImageResId 함수는 ID의 의미가 모호하고,
// 이제 getDefaultProfileImageNumber 함수가 사용자 ID를 인자로 받지 않으므로,
// 이 함수는 더 이상 직접적으로 사용되지 않거나, User 객체를 받는 형태로 완전히 대체되어야 합니다.
// 아래는 User 객체를 받아 처리하는 함수의 업데이트된 예시입니다.

/*
import com.ssafy.lanterns.data.model.User // User 모델 import 필요

@DrawableRes
fun getEffectiveProfileImageRes(user: User): Int {
    // User 모델에 selectedProfileImageNumber: Int? 필드가 있다고 가정 (null은 선택 안 함)
    val selectedNumber = user.selectedProfileImageNumber
    return if (selectedNumber != null && selectedNumber in 1..15) {
        getProfileImageByNumber(selectedNumber)
    } else {
        // 선택된 이미지가 없으면 기본 이미지 (1번) 사용
        getProfileImageByNumber(getDefaultProfileImageNumber())
    }
}
*/

// Helper function to get drawable resource ID based on user/chat ID
// Assumes profile images are named profile_1.png to profile_15.png in res/drawable/
// !! 주의: 이 함수는 getDefaultProfileImageNumber() 와 역할이 중복될 수 있으며,
// !! User.selectedProfileImageNumber 와 함께 사용하는 getEffectiveProfileImageRes 와 같은
// !! 명확한 함수로 대체하는 것을 권장합니다.
@DrawableRes
fun getProfileImageResId(id: Int): Int {
    if (id == -1) {
        return R.drawable.public_1 // 확성기 아이콘 (모두의 광장)
    }
    // Ensure imageNumber is within 1-15 range using modulo
    val imageNumber = (id - 1) % 15 + 1
    return when (imageNumber) {
        1 -> R.drawable.profile_1
        2 -> R.drawable.profile_2
        3 -> R.drawable.profile_3
        4 -> R.drawable.profile_4
        5 -> R.drawable.profile_5
        6 -> R.drawable.profile_6
        7 -> R.drawable.profile_7
        8 -> R.drawable.profile_8
        9 -> R.drawable.profile_9
        10 -> R.drawable.profile_10
        11 -> R.drawable.profile_11
        12 -> R.drawable.profile_12
        13 -> R.drawable.profile_13
        14 -> R.drawable.profile_14
        15 -> R.drawable.profile_15
        else -> R.drawable.lantern_image // Fallback image
    }
} 