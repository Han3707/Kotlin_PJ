package com.example.bletest.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * 애플리케이션 수준의 의존성을 제공하는 Hilt 모듈
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * 애플리케이션 수명주기 동안 유지되는 코루틴 스코프를 제공
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

/**
 * 애플리케이션 수준의 코루틴 스코프를 위한 한정자
 */
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class ApplicationScope 