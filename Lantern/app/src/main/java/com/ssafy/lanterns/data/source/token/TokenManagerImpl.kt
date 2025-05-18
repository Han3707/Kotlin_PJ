package com.ssafy.lanterns.data.source.token

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

@Singleton
class TokenManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : TokenManager {

    private object PreferencesKeys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val USER_ID = longPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
        val NICKNAME = stringPreferencesKey("nickname")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
    }

    override suspend fun saveAccessToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCESS_TOKEN] = token
            // 토큰이 저장되면 로그인 상태로 설정
            preferences[PreferencesKeys.IS_LOGGED_IN] = true
        }
    }

    override suspend fun getAccessToken(): String? {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.ACCESS_TOKEN]
        }.first()
    }

    override suspend fun saveUserInfo(userId: Long, email: String, nickname: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_ID] = userId
            preferences[PreferencesKeys.EMAIL] = email
            preferences[PreferencesKeys.NICKNAME] = nickname
            // 사용자 정보가 저장되면 로그인 상태로 설정
            preferences[PreferencesKeys.IS_LOGGED_IN] = true
        }
    }

    override suspend fun getUserId(): Long? {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.USER_ID]
        }.first()
    }

    override suspend fun getEmail(): String? {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.EMAIL]
        }.first()
    }

    override suspend fun getNickname(): String? {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.NICKNAME]
        }.first()
    }

    override suspend fun isLoggedIn(): Boolean {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.IS_LOGGED_IN] ?: false
        }.first()
    }

    override suspend fun clearTokenAndUserInfo() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.ACCESS_TOKEN)
            preferences.remove(PreferencesKeys.USER_ID)
            preferences.remove(PreferencesKeys.EMAIL)
            preferences.remove(PreferencesKeys.NICKNAME)
            preferences[PreferencesKeys.IS_LOGGED_IN] = false
        }
    }
} 