package com.ssafy.lantern.di

import android.content.Context
import com.ssafy.lantern.service.MeshService
import com.ssafy.lantern.service.ble.BleComm
import com.ssafy.lantern.service.ble.BleCommImpl
import com.ssafy.lantern.service.ble.ConnectionManager
import com.ssafy.lantern.service.network.MeshNetworkLayer
import com.ssafy.lantern.service.provisioning.ProvisioningManager
import com.ssafy.lantern.service.security.SecurityManager
import com.ssafy.lantern.service.session.SessionManager
import com.ssafy.lantern.service.transport.TransportLayer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * BLE 메쉬 네트워크 관련 의존성 모듈
 * 순수 Android API를 사용한 구현을 제공합니다.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MeshModule {
    
    /**
     * BLE 통신 인터페이스 바인딩
     */
    @Binds
    @Singleton
    abstract fun provideBleComm(bleCommImpl: BleCommImpl): BleComm
    
    companion object {
        /**
         * 전송 레이어 제공
         */
        @Provides
        @Singleton
        fun provideTransportLayer(): TransportLayer {
            return TransportLayer()
        }
        
        /**
         * 보안 관리자 제공
         */
        @Provides
        @Singleton
        fun provideSecurityManager(): SecurityManager {
            return SecurityManager()
        }
        
        /**
         * 네트워크 레이어 제공
         */
        @Provides
        @Singleton
        fun provideMeshNetworkLayer(
            bleComm: BleComm,
            transportLayer: TransportLayer
        ): MeshNetworkLayer {
            return MeshNetworkLayer(bleComm, transportLayer)
        }
        
        /**
         * 프로비저닝 관리자 제공
         */
        @Provides
        @Singleton
        fun provideProvisioningManager(
            @ApplicationContext context: Context,
            securityManager: SecurityManager
        ): ProvisioningManager {
            return ProvisioningManager(context, securityManager)
        }
        
        /**
         * 연결 관리자 제공
         */
        @Provides
        @Singleton
        fun provideConnectionManager(
            @ApplicationContext context: Context
        ): ConnectionManager {
            return ConnectionManager(context)
        }
        
        /**
         * 세션 관리자 제공
         */
        @Provides
        @Singleton
        fun provideSessionManager(
            @ApplicationContext context: Context
        ): SessionManager {
            return SessionManager(context)
        }
        
        /**
         * 메쉬 서비스 제공
         */
        @Provides
        @Singleton
        fun provideMeshService(
            @ApplicationContext context: Context,
            bleComm: BleComm,
            networkLayer: MeshNetworkLayer,
            securityManager: SecurityManager,
            provisioningManager: ProvisioningManager,
            connectionManager: ConnectionManager,
            sessionManager: SessionManager
        ): MeshService {
            return MeshService(
                context,
                bleComm, 
                networkLayer, 
                securityManager, 
                provisioningManager,
                connectionManager,
                sessionManager
            )
        }
    }
} 