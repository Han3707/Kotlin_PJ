package com.ssafy.lanterns.di

import android.content.Context
import com.ssafy.lanterns.service.ble.advertiser.NeighborAdvertiser
import com.ssafy.lanterns.service.ble.scanner.NeighborScanner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BleServiceModule {

    @Provides
    @Singleton
    fun provideNeighborScanner(@ApplicationContext context: Context): NeighborScanner {
        // NeighborScanner는 object 키워드로 싱글톤으로 선언되어 있으므로,
        // ApplicationContext를 직접 사용하지 않더라도 Hilt가 관리하는 싱글톤 인스턴스를 반환합니다.
        // init(activity)는 ViewModel에서 Activity를 얻었을 때 호출됩니다.
        return NeighborScanner
    }

    @Provides
    @Singleton
    fun provideNeighborAdvertiser(@ApplicationContext context: Context): NeighborAdvertiser {
        // NeighborAdvertiser는 object 키워드로 싱글톤으로 선언되어 있으므로,
        // Hilt가 관리하는 싱글톤 인스턴스를 반환합니다.
        // init(activity)는 ViewModel에서 Activity를 얻었을 때 호출됩니다.
        return NeighborAdvertiser
    }
} 