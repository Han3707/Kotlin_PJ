package com.example.bletest.data.repository

import android.bluetooth.BluetoothDevice
import com.example.bletest.data.model.ConnectionState
import com.example.bletest.data.model.DeviceConnectionState
import com.example.bletest.data.model.MessageData
import com.example.bletest.data.model.MessageType
import com.example.bletest.data.model.ScanResultData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * BLE 메시 네트워크의 핵심 기능을 추상화하는 리포지토리 인터페이스
 * BleDataSource를 통해 저수준 BLE 작업을 처리하며, 여러 BLE 연결 및 메시징 상태를 관리합니다.
 */
interface BleRepository {
    
    // --- 상태 Flow ---
    
    /**
     * BLE 스캔 결과를 나타내는 StateFlow
     */
    val scanResults: StateFlow<List<ScanResultData>>
    
    /**
     * 현재 BLE 연결 상태를 나타내는 StateFlow
     */
    val connectionState: StateFlow<DeviceConnectionState>
    
    /**
     * 수신된 메시지 목록을 나타내는 StateFlow
     */
    val messages: StateFlow<List<MessageData>>
    
    /**
     * 현재 스캔 중인지 여부를 나타내는 StateFlow
     */
    val isScanning: StateFlow<Boolean>
    
    /**
     * 연결 상태 맵을 제공하는 Flow (주소 -> 연결 상태)
     */
    val connectionStates: Flow<Map<String, ConnectionState>>
    
    /**
     * 메시 네트워크의 노드(기기)들을 제공하는 Flow (DeviceConnectionState로 대체)
     */
    // val networkNodes: Flow<Set<NetworkNode>> // 삭제
    
    // --- 메시 네트워크 제어 ---
    
    /**
     * 메시 네트워크 시작 (startMeshNetworking 으로 대체되었으므로 제거)
     *
     * @param deviceId 현재 기기의 ID
     * @return 시작 성공 여부
     */
    // suspend fun startMesh(deviceId: String): Boolean // 함수 선언 제거
    
    /**
     * 메시 네트워크 중지 (stopMeshNetworking 으로 대체되었으므로 제거)
     *
     * @return 중지 성공 여부
     */
    // suspend fun stopMesh(): Boolean // 함수 선언 제거
    
    /**
     * 메시 네트워크가 실행 중인지 확인
     * 
     * @return 실행 중이면 true, 아니면 false
     */
    fun isMeshRunning(): Boolean
    
    /**
     * 메시 네트워킹 시작 (광고 및 스캔 동시 수행 - 공용 채팅방용)
     * 
     * @param deviceId 현재 기기의 ID
     * @return 시작 성공 여부
     */
    suspend fun startMeshNetworking(deviceId: String): Boolean
    
    /**
     * 메시 네트워킹 중지 (광고, 스캔, 연결 모두 중지 - 공용 채팅방용)
     * 
     * @return 중지 성공 여부
     */
    suspend fun stopMeshNetworking(): Boolean
    
    /**
     * 브로드캐스트 메시지 전송 (공용 채팅방용)
     * 
     * @param content 메시지 내용
     * @return 전송 성공 여부
     */
    suspend fun sendBroadcastMessage(content: String): Boolean
    
    /**
     * BLE 서비스 초기화
     */
    fun initBleService()
    
    // --- BLE 동작 ---
    
    /**
     * BLE 장치 스캔 시작
     */
    fun startScan()
    
    /**
     * BLE 장치 스캔 중지
     */
    fun stopScan()
    
    /**
     * 특정 BLE 장치에 연결
     *
     * @param device 연결할 BluetoothDevice
     */
    fun connect(device: BluetoothDevice)
    
    /**
     * BLE 장치 연결 해제
     */
    fun disconnect()
    
    /**
     * 리소스 해제
     */
    fun close()
    
    /**
     * 메시지 전송
     *
     * @param targetId 대상 장치 ID (null이면 브로드캐스트)
     * @param messageType 메시지 타입
     * @param content 메시지 내용
     * @return 전송 성공 여부
     */
    suspend fun sendMessage(targetId: String?, messageType: MessageType, content: String): Boolean
    
    /**
     * 장치 ID 반환
     */
    fun getDeviceId(): String
    
    /**
     * 장치 ID 설정
     *
     * @param id 설정할 장치 ID
     */
    fun setDeviceId(id: String)
    
    /**
     * 장치 이름 반환
     */
    fun getDeviceName(): String
    
    /**
     * BLE 서비스가 연결되어 있는지 확인
     */
    fun isServiceConnected(): Boolean
} 