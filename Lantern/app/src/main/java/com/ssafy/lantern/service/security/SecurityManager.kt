package com.ssafy.lantern.service.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.nio.ByteBuffer
import java.security.Key
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 키 타입 정의
 */
enum class KeyType {
    NETWORK_KEY,    // 네트워크 키 (기본 보안)
    APPLICATION_KEY, // 애플리케이션 키 (상위 레이어)
    DEVICE_KEY,     // 기기 고유 키 (프로비저닝)
    PRIVACY_KEY     // 프라이버시 키 (네트워크 ID 보호)
}

/**
 * 메쉬 네트워크 보안 관리자
 * 키 파생, 암호화, 복호화 기능 제공
 */
@Singleton
class SecurityManager @Inject constructor() {
    companion object {
        private const val TAG = "SecurityManager"
        
        // AES-GCM 태그 크기 (바이트)
        private const val AUTH_TAG_SIZE = 16
        
        // AES-GCM 논스 크기 (바이트)
        private const val NONCE_SIZE = 12
        
        // 안드로이드 키스토어 이름
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        
        // 마스터 키 별칭
        private const val MASTER_KEY_ALIAS = "com.ssafy.lantern.MASTER_KEY"
        
        // HKDF 정보 문자열
        private const val HKDF_INFO_NETWORK = "mesh network key"
        private const val HKDF_INFO_APP = "mesh application key"
        private const val HKDF_INFO_DEVICE = "mesh device key"
        private const val HKDF_INFO_PRIVACY = "mesh privacy key"
        
        // 키 갱신 주기 (밀리초)
        private const val KEY_REFRESH_INTERVAL = 3600_000L * 24 * 7  // 1주일
    }
    
    // 파생된 키 저장소
    private val keyStore = ConcurrentHashMap<KeyType, ByteArray>()
    
    // 키 생성 타임스탬프
    private val keyTimestamps = ConcurrentHashMap<KeyType, Long>()
    
    // 보안 난수 생성기
    private val secureRandom = SecureRandom()
    
    /**
     * HKDF 구현 (RFC 5869)
     * HMAC 기반 키 파생 함수
     */
    private fun hkdf(inputKeyMaterial: ByteArray, salt: ByteArray, info: ByteArray, outputLength: Int): ByteArray {
        // 1단계: HMAC-SHA-256으로 추출
        val prk = hmacSha256(salt, inputKeyMaterial)
        
        // 2단계: 확장
        val n = (outputLength + 31) / 32  // 필요한 출력 블록 수 (32바이트씩)
        val okm = ByteArray(n * 32)
        var previous = ByteArray(0)
        
        for (i in 0 until n) {
            // T(i) = HMAC-SHA-256(PRK, T(i-1) | info | i+1)
            val data = ByteBuffer.allocate(previous.size + info.size + 1)
                .put(previous)
                .put(info)
                .put((i + 1).toByte())
                .array()
            
            val t = hmacSha256(prk, data)
            System.arraycopy(t, 0, okm, i * 32, t.size)
            previous = t
        }
        
        // 요청한 길이만큼 반환
        return okm.copyOfRange(0, outputLength)
    }
    
    /**
     * HMAC-SHA-256 계산
     */
    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(key, "HmacSHA256")
        mac.init(secretKey)
        return mac.doFinal(data)
    }
    
    /**
     * 마스터 키로부터 네트워크/앱/프라이버시 키 파생
     */
    fun deriveKeys(masterKey: ByteArray) {
        try {
            Log.d(TAG, "마스터 키로부터 키 파생 시작")
            
            // 솔트 생성 (32바이트)
            val salt = ByteArray(32)
            secureRandom.nextBytes(salt)
            
            // 현재 시간 기록
            val currentTime = System.currentTimeMillis()
            
            // 네트워크 키 파생
            val networkKey = hkdf(
                masterKey,
                salt,
                HKDF_INFO_NETWORK.toByteArray(),
                16
            )
            keyStore[KeyType.NETWORK_KEY] = networkKey
            keyTimestamps[KeyType.NETWORK_KEY] = currentTime
            Log.d(TAG, "네트워크 키 파생 완료")
            
            // 앱 키 파생
            val appKey = hkdf(
                networkKey,  // 네트워크 키로부터 파생
                salt,
                HKDF_INFO_APP.toByteArray(),
                16
            )
            keyStore[KeyType.APPLICATION_KEY] = appKey
            keyTimestamps[KeyType.APPLICATION_KEY] = currentTime
            Log.d(TAG, "앱 키 파생 완료")
            
            // 프라이버시 키 파생
            val privacyKey = hkdf(
                networkKey,  // 네트워크 키로부터 파생
                salt,
                HKDF_INFO_PRIVACY.toByteArray(),
                16
            )
            keyStore[KeyType.PRIVACY_KEY] = privacyKey
            keyTimestamps[KeyType.PRIVACY_KEY] = currentTime
            Log.d(TAG, "프라이버시 키 파생 완료")
        } catch (e: Exception) {
            Log.e(TAG, "키 파생 중 오류 발생", e)
        }
    }
    
    /**
     * 장치 키 파생 (프로비저닝 시 사용)
     */
    fun deriveDeviceKey(deviceRandom: ByteArray, provisionerRandom: ByteArray): ByteArray {
        try {
            // 솔트 생성
            val salt = ByteArray(32)
            secureRandom.nextBytes(salt)
            
            // 장치 고유 정보와 프로비저너 랜덤 값 결합
            val combinedInput = ByteBuffer.allocate(deviceRandom.size + provisionerRandom.size)
                .put(deviceRandom)
                .put(provisionerRandom)
                .array()
            
            // 장치 키 파생
            val deviceKey = hkdf(
                combinedInput,
                salt,
                HKDF_INFO_DEVICE.toByteArray(),
                16
            )
            
            keyStore[KeyType.DEVICE_KEY] = deviceKey
            keyTimestamps[KeyType.DEVICE_KEY] = System.currentTimeMillis()
            Log.d(TAG, "장치 키 파생 완료")
            
            return deviceKey
        } catch (e: Exception) {
            Log.e(TAG, "장치 키 파생 중 오류 발생", e)
            return ByteArray(16)  // 빈 키 반환 (오류)
        }
    }
    
    /**
     * 키 갱신이 필요한지 확인
     */
    fun isKeyRefreshNeeded(keyType: KeyType): Boolean {
        val timestamp = keyTimestamps[keyType] ?: return true
        val currentTime = System.currentTimeMillis()
        return currentTime - timestamp > KEY_REFRESH_INTERVAL
    }
    
    /**
     * AES-GCM 암호화
     * 
     * @param plaintext 평문 데이터
     * @param keyType 사용할 키 타입
     * @return 암호화된 데이터 (IV + 암호문 + 인증 태그)
     */
    fun encrypt(plaintext: ByteArray, keyType: KeyType): ByteArray {
        val key = keyStore[keyType] ?: throw IllegalStateException("키가 파생되지 않았습니다: $keyType")
        
        try {
            // IV(Nonce) 생성
            val iv = ByteArray(NONCE_SIZE)
            secureRandom.nextBytes(iv)
            
            // AES-GCM 설정
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val secretKey = SecretKeySpec(key, "AES")
            val gcmSpec = GCMParameterSpec(AUTH_TAG_SIZE * 8, iv)
            
            // 암호화
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
            val ciphertext = cipher.doFinal(plaintext)
            
            // IV + 암호문 결합
            val result = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, result, 0, iv.size)
            System.arraycopy(ciphertext, 0, result, iv.size, ciphertext.size)
            
            return result
        } catch (e: Exception) {
            Log.e(TAG, "암호화 중 오류 발생", e)
            throw e
        }
    }
    
    /**
     * AES-GCM 복호화
     * 
     * @param encryptedData 암호화된 데이터 (IV + 암호문 + 인증 태그)
     * @param keyType 사용할 키 타입
     * @return 복호화된 평문 또는 null (인증 실패 시)
     */
    fun decrypt(encryptedData: ByteArray, keyType: KeyType): ByteArray? {
        val key = keyStore[keyType] ?: throw IllegalStateException("키가 파생되지 않았습니다: $keyType")
        
        try {
            // IV 추출
            if (encryptedData.size < NONCE_SIZE) {
                Log.e(TAG, "암호문이 너무 짧습니다: ${encryptedData.size} 바이트")
                return null
            }
            
            val iv = encryptedData.copyOfRange(0, NONCE_SIZE)
            val ciphertext = encryptedData.copyOfRange(NONCE_SIZE, encryptedData.size)
            
            // AES-GCM 설정
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val secretKey = SecretKeySpec(key, "AES")
            val gcmSpec = GCMParameterSpec(AUTH_TAG_SIZE * 8, iv)
            
            // 복호화
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            return cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            Log.e(TAG, "복호화 중 오류 발생 (인증 실패 가능성)", e)
            return null
        }
    }
    
    /**
     * 네트워크 ID 생성 (프라이버시 키 기반)
     */
    fun generateNetworkId(): ByteArray {
        val privacyKey: ByteArray = keyStore[KeyType.PRIVACY_KEY] 
            ?: throw IllegalStateException("프라이버시 키가 파생되지 않았습니다")
        
        // 네트워크 ID = SHA-256(프라이버시 키)의 첫 8바이트
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(privacyKey)
        return hash.copyOfRange(0, 8)
    }
    
    /**
     * 키 정보 지우기 (완전 초기화)
     */
    fun clearKeys() {
        keyStore.clear()
        keyTimestamps.clear()
        Log.d(TAG, "모든 키 정보가 초기화되었습니다")
    }
} 