package com.ssafy.lantern.di // 실제 패키지 경로에 맞게 수정

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
// import androidx.credentials.CredentialManager // 제거되었으므로 주석 처리 또는 삭제
import androidx.room.Room
import com.ssafy.lantern.data.database.AppDatabase
import com.ssafy.lantern.data.repository.*
import com.ssafy.lantern.data.source.ble.advertiser.AdvertiserManager
import com.ssafy.lantern.data.source.ble.gatt.GattClientManager
import com.ssafy.lantern.data.source.ble.gatt.GattServerManager
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
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "lantern.db"
            )
                .fallbackToDestructiveMigration() // 주의: 마이그레이션 전략 필요시 수정
                .build()
        }

        @Provides
        @Singleton
        fun provideUserDao(appDatabase: AppDatabase): UserDao {
            return appDatabase.userDao()
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