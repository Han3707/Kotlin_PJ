package com.ssafy.lanterns.di

import android.content.Context
// import androidx.credentials.CredentialManager // 제거되었으므로 주석 처리 또는 삭제
import androidx.room.Room
import com.ssafy.lanterns.data.database.AppDatabase
import com.ssafy.lanterns.data.model.Message
import com.ssafy.lanterns.data.repository.*
import com.ssafy.lanterns.data.source.ble.advertiser.AdvertiserManager
import com.ssafy.lanterns.data.source.ble.gatt.GattClientManager
import com.ssafy.lanterns.data.source.ble.gatt.GattServerManager
import com.ssafy.lanterns.data.source.local.dao.ChatRoomDao
import com.ssafy.lanterns.data.source.local.dao.MessageDao
import com.ssafy.lanterns.data.source.local.dao.UserDao
import com.ssafy.lanterns.data.source.local.dao.FollowDao
import com.ssafy.lanterns.data.source.local.dao.CallListDao
// import com.ssafy.lantern.data.source.ble.scanner.ScannerManager // 제거
// Hilt import 추가
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Module을 추상 클래스로 변경하고 Binds 사용 권장
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule { // object -> abstract class

    // UserDao, UserRepository 등 기존 @Provides 함수들은 companion object로 이동
    companion object {
        @Provides
        @Singleton
        fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
            return AppDatabase.buildDatabase(context)
        }

        @Provides
        @Singleton
        fun provideUserDao(appDatabase: AppDatabase): UserDao {
            return appDatabase.userDao()
        }
        
        @Provides
        @Singleton
        fun provideChatRoomDao(appDatabase: AppDatabase): ChatRoomDao {
            return appDatabase.chatRoomDao()
        }
        
        @Provides
        @Singleton
        fun provideMessageDao(appDatabase: AppDatabase): MessageDao {
            return appDatabase.messageDao()
        }
        
        @Provides
        @Singleton
        fun provideFollowDao(appDatabase: AppDatabase): FollowDao {
            return appDatabase.followDao()
        }
        
        @Provides
        @Singleton
        fun provideCallListDao(appDatabase: AppDatabase): CallListDao {
            return appDatabase.callListDao()
        }

        // UserRepository 제공 메서드 추가
        @Provides
        @Singleton
        fun provideUserRepositoryImpl(
            userDao: UserDao,
            chatRoomDao: ChatRoomDao,
            messageDao: MessageDao,
            followDao: FollowDao,
            callListDao: CallListDao,
            @ApplicationContext context: Context
        ): UserRepositoryImpl {
            return UserRepositoryImpl(userDao, chatRoomDao, messageDao, followDao, callListDao, context)
        }

        // AdvertiserManager, GattServerManager, GattClientManager @Provides도 여기로 이동
        @Provides
        @Singleton
        fun provideAdvertiserManager(@ApplicationContext context: Context): AdvertiserManager {
            return AdvertiserManager(context)
        }

        @Provides
        @Singleton
        fun provideGattServerManager(@ApplicationContext context: Context): GattServerManager {
            return GattServerManager(context, { _, _, _ -> }, { _ -> }, { _ -> })
        }

        @Provides
        @Singleton
        fun provideGattClientManager(@ApplicationContext context: Context): GattClientManager {
            return GattClientManager(context, { _, _, _ -> }, { _, _ -> })
        }
    }

    // AuthRepository 바인딩 (Binds 사용)
    // 구글 로그인 구현체 (현재는 사용하지 않음)
    // @Binds
    // @Singleton
    // abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    // JWT 인증 구현체
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryGoogleImpl): AuthRepository

    // UserRepository 바인딩 (Binds 사용)
    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    // DeviceRepository 바인딩 추가
    @Binds
    @Singleton
    abstract fun bindDeviceRepository(impl: DeviceRepositoryImpl): DeviceRepository

    // ChatRepository 바인딩 추가
    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    // CredentialManager 관련 코드 없음

    // --- 리팩토링된 BLE 매니저 Provider --- //

    // ScannerManager는 ScanCallback을 ViewModel에서 동적으로 생성/전달해야 하므로
    // Hilt Singleton 주입에는 적합하지 않음. ViewModel에서 직접 생성하여 사용.
    /*
    @Provides
    @Singleton // 또는 다른 범위
    fun provideScannerManager(@ApplicationContext context: Context): ScannerManager {
       // 콜백 문제로 주입 대신 ViewModel에서 직접 생성
       // return ScannerManager(context, { _ -> })
    }
    */

    // PermissionHelper는 Activity가 필요하므로 ViewModel 주입 부적합.
    // Composable에서 직접 처리 (Accompanist 사용)
}