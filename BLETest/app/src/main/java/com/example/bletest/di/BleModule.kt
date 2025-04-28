package com.example.bletest.di

import android.content.Context
import com.example.bletest.data.repository.BleRepository
import com.example.bletest.data.repository.BleRepositoryImpl
import com.example.bletest.data.source.ble.BleDataSource
import com.example.bletest.data.source.ble.BleDataSourceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/**
 * BLE 관련 의존성을 제공하는 Hilt 모듈
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BleModule {
    
    /**
     * BleDataSource 인터페이스에 대한 구현체 제공
     */
    @Binds
    @Singleton
    abstract fun provideBleDataSource(bleDataSourceImpl: BleDataSourceImpl): BleDataSource
    
    /**
     * BleRepository 인터페이스에 대한 구현체 제공
     */
    @Binds
    @Singleton
    abstract fun provideBleRepository(bleRepositoryImpl: BleRepositoryImpl): BleRepository
} 