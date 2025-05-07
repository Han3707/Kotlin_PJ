package com.ssafy.lantern.ui.util

import androidx.annotation.DrawableRes
import com.ssafy.lantern.R

// Helper function to get drawable resource ID based on user/chat ID
// Assumes profile images are named profile_1.png to profile_15.png in res/drawable/
@DrawableRes
fun getProfileImageResId(id: Int): Int {
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