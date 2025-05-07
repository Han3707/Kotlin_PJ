package com.ssafy.lantern.di

import android.content.Context
import com.ssafy.lantern.MyApp
import com.ssafy.lantern.service.MeshService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import no.nordicsemi.android.mesh.MeshManagerApi
import javax.inject.Singleton

/**
 * BLE Mesh 관련 의존성을 제공하는 Hilt 모듈
 */
@Module
@InstallIn(SingletonComponent::class)
object MeshModule {
    
    /**
     * MeshManagerApi 싱글톤 인스턴스 제공
     */
    @Provides
    @Singleton
    fun provideMeshManagerApi(@ApplicationContext context: Context): MeshManagerApi {
        return (context.applicationContext as MyApp).meshManagerApi
    }
    
    /**
     * MeshService 싱글톤 인스턴스 제공
     */
    @Provides
    @Singleton
    fun provideMeshService(@ApplicationContext context: Context): MeshService {
        return MeshService(context)
    }
} 