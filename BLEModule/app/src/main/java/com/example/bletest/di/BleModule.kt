package com.example.bletest.di

import android.bluetooth.BluetoothManager
import android.content.Context
import com.example.bletest.data.repository.BleRepository
import com.example.bletest.data.repository.BleRepositoryImpl
import com.example.bletest.data.source.ble.BleDataSource
import com.example.bletest.data.source.ble.BleDataSourceImpl
import dagger.Module
import dagger.Provides
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
object BleModule {
    
    @Provides
    @Singleton
    fun provideBluetoothManager(
        @ApplicationContext context: Context
    ): BluetoothManager {
        return context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    
    /**
     * BleDataSource 인터페이스에 대한 구현체 제공
     */
    @Provides
    @Singleton
    fun provideBleDataSource(
        bleDataSourceImpl: BleDataSourceImpl
    ): BleDataSource {
        return bleDataSourceImpl
    }
    
    /**
     * BleRepository 인터페이스에 대한 구현체 제공
     */
    @Provides
    @Singleton
    fun provideBleRepository(
        bleRepositoryImpl: BleRepositoryImpl
    ): BleRepository {
        return bleRepositoryImpl
    }
} 