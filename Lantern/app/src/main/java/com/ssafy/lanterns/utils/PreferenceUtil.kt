package com.ssafy.lanterns.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences를 쉽게 사용하기 위한 유틸리티 클래스
 * 로그인 토큰 및 사용자 기본 설정을 저장하는 데 사용됩니다.
 */
class PreferenceUtil(context: Context) {
    private val prefs: SharedPreferences = 
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    
    /**
     * 문자열 값을 저장합니다.
     */
    fun setString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
    
    /**
     * 문자열 값을 불러옵니다.
     */
    fun getString(key: String, defValue: String = ""): String {
        return prefs.getString(key, defValue) ?: defValue
    }
    
    /**
     * 불리언 값을 저장합니다.
     */
    fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
    
    /**
     * 불리언 값을 불러옵니다.
     */
    fun getBoolean(key: String, defValue: Boolean = false): Boolean {
        return prefs.getBoolean(key, defValue)
    }
    
    /**
     * 정수 값을 저장합니다.
     */
    fun setInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }
    
    /**
     * 정수 값을 불러옵니다.
     */
    fun getInt(key: String, defValue: Int = 0): Int {
        return prefs.getInt(key, defValue)
    }
    
    /**
     * 로그인 토큰을 저장합니다.
     */
    fun saveToken(token: String) {
        setString(KEY_TOKEN, token)
    }
    
    /**
     * 로그인 토큰을 불러옵니다.
     */
    fun getToken(): String {
        return getString(KEY_TOKEN)
    }
    
    /**
     * 로그인 상태를 저장합니다.
     */
    fun saveLoginState(isLoggedIn: Boolean) {
        setBoolean(KEY_IS_LOGGED_IN, isLoggedIn)
    }
    
    /**
     * 로그인 상태를 확인합니다.
     */
    fun isLoggedIn(): Boolean {
        return getBoolean(KEY_IS_LOGGED_IN)
    }
    
    /**
     * 사용자 정보를 모두 삭제합니다. (로그아웃 시 호출)
     */
    fun clearUserInfo() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_IS_LOGGED_IN)
            .apply()
    }
    
    companion object {
        private const val PREFERENCES_NAME = "lantern_prefs"
        private const val KEY_TOKEN = "token"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }
} 