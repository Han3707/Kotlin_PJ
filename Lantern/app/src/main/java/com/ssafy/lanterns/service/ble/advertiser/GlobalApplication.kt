package com.ssafy.lanterns.service.ble.advertiser

import android.app.Application
import android.content.Context

/**
 * 어플리케이션 객체를 글로벌하게 접근하기 위한 유틸리티 클래스
 * BLE 관련 컴포넌트에서 Context가 필요할 때 활용
 */
object GlobalApplication {
    private var application: Application? = null

    fun setGlobalApplication(app: Application) {
        application = app
    }

    fun getGlobalApplicationContext(): Context? {
        return application?.applicationContext
    }
} 