package com.ssafy.lantern.service

import android.content.Context
import android.util.Log
import com.ssafy.lantern.MyApp
import com.ssafy.lantern.data.model.ChatMessage
import com.ssafy.lantern.utils.JsonUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import no.nordicsemi.android.mesh.MeshManagerApi
import no.nordicsemi.android.mesh.MeshManagerCallbacks
import no.nordicsemi.android.mesh.MeshNetwork
import no.nordicsemi.android.mesh.transport.MeshMessage
import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode
import no.nordicsemi.android.mesh.transport.VendorModelMessageStatus
import no.nordicsemi.android.mesh.transport.VendorModelMessageUnacked
import no.nordicsemi.android.mesh.utils.MeshAddress
import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode
import no.nordicsemi.android.mesh.provisionerstates.ProvisioningState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BLE Mesh 네트워크 관리 및 메시지 전송을 담당하는 서비스
 */
@Singleton
class MeshService @Inject constructor(
    @ApplicationContext private val context: Context
) : MeshManagerCallbacks {
    
    private val TAG = "MeshService"
    
    // 메시지 수신 리스너 인터페이스
    interface MessageListener {
        fun onMessageReceived(message: ChatMessage)
    }
    
    // 상태 변경 리스너 인터페이스
    interface StatusListener {
        fun onNetworkStatusChanged(isConnected: Boolean)
        fun onProvisioningComplete(unicastAddress: Int)
    }
    
    private val meshManager: MeshManagerApi by lazy {
        (context.applicationContext as MyApp).meshManagerApi
    }
    
    // 베벤더 모델 ID (실제 애플리케이션에 맞게 정의 필요)
    companion object {
        // SSAFY 벤더 ID (예시: 0xFFFF로 설정)
        const val VENDOR_ID = 0xFFFF
        // 채팅 모델 ID (예시: 0x0001로 설정)
        const val MODEL_ID = 0x0001
        // 브로드캐스트 주소
        const val BROADCAST_ADDRESS = 0xFFFF
        // 기본 TTL 값
        const val DEFAULT_TTL = 5
    }
    
    private val messageListeners = mutableListOf<MessageListener>()
    private val statusListeners = mutableListOf<StatusListener>()
    
    init {
        meshManager.setMeshManagerCallbacks(this)
        loadNetwork()
    }
    
    /**
     * 네트워크 로드 및 초기화
     */
    private fun loadNetwork() {
        try {
            meshManager.loadMeshNetwork()
        } catch (e: Exception) {
            Log.e(TAG, "메시 네트워크 로드 실패, 새 네트워크 생성: ${e.message}")
            createNewNetwork()
        }
    }
    
    /**
     * 새 메시 네트워크 생성
     */
    private fun createNewNetwork() {
        meshManager.createMeshNetwork()
        // Note: We don't need to manually save the network anymore in 3.3.4
    }
    
    /**
     * 메시지 수신 리스너 등록
     */
    fun addMessageListener(listener: MessageListener) {
        if (!messageListeners.contains(listener)) {
            messageListeners.add(listener)
        }
    }
    
    /**
     * 메시지 수신 리스너 제거
     */
    fun removeMessageListener(listener: MessageListener) {
        messageListeners.remove(listener)
    }
    
    /**
     * 상태 변경 리스너 등록
     */
    fun addStatusListener(listener: StatusListener) {
        if (!statusListeners.contains(listener)) {
            statusListeners.add(listener)
        }
    }
    
    /**
     * 상태 변경 리스너 제거
     */
    fun removeStatusListener(listener: StatusListener) {
        statusListeners.remove(listener)
    }
    
    /**
     * 유니캐스트 메시지 전송 (1:1 채팅)
     */
    fun sendUnicastMessage(targetAddress: Int, content: String, senderName: String = "") {
        val localAddress = getLocalUnicastAddress()
        if (localAddress == null) {
            Log.e(TAG, "로컬 주소를 찾을 수 없습니다.")
            return
        }
        
        val msg = ChatMessage(
            fromAddress = localAddress,
            toAddress = targetAddress,
            timestamp = System.currentTimeMillis(),
            content = content,
            senderName = senderName
        )
        
        val pdu = JsonUtil.toBytes(msg)
        try {
            // 3.3.4 버전에서는 다른 방식으로 메시지 생성 및 전송
            val vendorMessage = VendorModelMessageUnacked(
                VENDOR_ID,
                MODEL_ID,
                targetAddress,
                pdu
            )
            // 메시지 전송
            meshManager.createMeshPdu(targetAddress, vendorMessage)
            
            // 자신의 화면에도 메시지 표시
            notifyMessageReceived(msg)
        } catch (e: Exception) {
            Log.e(TAG, "메시지 전송 실패: ${e.message}")
        }
    }
    
    /**
     * 브로드캐스트 메시지 전송 (공용 채팅)
     */
    fun sendBroadcastMessage(content: String, senderName: String = "") {
        val localAddress = getLocalUnicastAddress()
        if (localAddress == null) {
            Log.e(TAG, "로컬 주소를 찾을 수 없습니다.")
            return
        }
        
        val msg = ChatMessage(
            fromAddress = localAddress,
            toAddress = BROADCAST_ADDRESS,
            timestamp = System.currentTimeMillis(),
            content = content,
            senderName = senderName
        )
        
        val pdu = JsonUtil.toBytes(msg)
        try {
            // 3.3.4 버전에서는 다른 방식으로 메시지 생성 및 전송
            val vendorMessage = VendorModelMessageUnacked(
                VENDOR_ID,
                MODEL_ID,
                BROADCAST_ADDRESS,
                pdu
            )
            // 메시지 전송
            meshManager.createMeshPdu(BROADCAST_ADDRESS, vendorMessage)
            
            // 자신의 화면에도 메시지 표시
            notifyMessageReceived(msg)
        } catch (e: Exception) {
            Log.e(TAG, "브로드캐스트 메시지 전송 실패: ${e.message}")
        }
    }
    
    /**
     * 로컬 유니캐스트 주소 가져오기
     */
    private fun getLocalUnicastAddress(): Int? {
        val network = meshManager.meshNetwork ?: return null
        val provisioner = network.selectedProvisioner ?: return null
        
        // 첫 번째 유니캐스트 주소 할당 범위 사용
        val range = provisioner.allocatedUnicastRanges.firstOrNull() ?: return null
        return range.lowAddress
    }
    
    /**
     * 수신된 메시지를 리스너에게 알림
     */
    private fun notifyMessageReceived(message: ChatMessage) {
        messageListeners.forEach { it.onMessageReceived(message) }
    }
    
    /**
     * 네트워크 상태 변경을 리스너에게 알림
     */
    private fun notifyNetworkStatusChanged(isConnected: Boolean) {
        statusListeners.forEach { it.onNetworkStatusChanged(isConnected) }
    }
    
    /**
     * 프로비저닝 완료를 리스너에게 알림
     */
    private fun notifyProvisioningComplete(unicastAddress: Int) {
        statusListeners.forEach { it.onProvisioningComplete(unicastAddress) }
    }
    
    // --- MeshManagerCallbacks 구현 (Nordic BLE Mesh Library 3.3.4 버전) --- //
    
    override fun onNetworkLoaded(meshNetwork: MeshNetwork?) {
        Log.d(TAG, "메시 네트워크 로드됨")
        meshNetwork?.let {
            // 네트워크 설정 (필요한 경우)
            notifyNetworkStatusChanged(true)
        }
    }
    
    override fun onNetworkUpdated(meshNetwork: MeshNetwork?) {
        Log.d(TAG, "메시 네트워크 업데이트됨")
        // Note: We don't need to manually save the network anymore in 3.3.4
    }
    
    override fun onNetworkLoadFailed(error: String?) {
        Log.e(TAG, "메시 네트워크 로드 실패: $error")
        createNewNetwork()
        notifyNetworkStatusChanged(false)
    }
    
    override fun onNetworkImported(meshNetwork: MeshNetwork?) {
        Log.d(TAG, "메시 네트워크 가져오기 성공")
    }
    
    override fun sendProvisioningPdu(meshNode: UnprovisionedMeshNode?, pdu: ByteArray?) {
        // 프로비저닝 중 PDU 전송 처리
        Log.d(TAG, "프로비저닝 PDU 전송")
    }
    
    override fun onMeshPduCreated(pdu: ByteArray?) {
        // 생성된 메시 PDU 처리
    }
    
    // 3.3.4 버전의 콜백 메서드
    override fun onProvisioningStateChanged(meshNode: UnprovisionedMeshNode?, state: ProvisioningState?, data: ByteArray?) {
        // 프로비저닝 상태 변경 처리
        if (state?.state == ProvisioningState.States.PROVISIONING_COMPLETE) {
            meshNode?.unicastAddress?.let { address ->
                notifyProvisioningComplete(address)
            }
        }
    }
    
    // 3.3.4 버전의 콜백 메서드
    override fun onMeshMessageReceived(src: Int, message: MeshMessage?) {
        // 메시 메시지 수신 처리
        Log.d(TAG, "메시 메시지 수신: $src")
        
        if (message is VendorModelMessageStatus) {
            val data = message.parameters
            val chatMessage = JsonUtil.fromBytesSafe(data)
            
            chatMessage?.let {
                // 수신 메시지 처리
                notifyMessageReceived(it)
            }
        }
    }
    
    // 3.3.4 버전의 콜백 메서드
    override fun onMeshMessageProcessed(dst: Int, message: MeshMessage?) {
        // 메시 메시지 처리 완료
        Log.d(TAG, "메시 메시지 처리 완료: $dst")
    }
    
    // 3.3.4 버전의 콜백 메서드
    override fun onTransactionFailed(dst: Int, message: MeshMessage?) {
        // 트랜잭션 실패 처리
        Log.e(TAG, "트랜잭션 실패: $dst")
    }
    
    // 3.3.4 버전의 콜백 메서드
    override fun onUnknownPduReceived(src: Int, accessPayload: ByteArray?) {
        // 알 수 없는 PDU 수신 처리
        Log.d(TAG, "알 수 없는 PDU 수신: $src")
        
        accessPayload?.let {
            val chatMessage = JsonUtil.fromBytesSafe(it)
            chatMessage?.let { msg ->
                notifyMessageReceived(msg)
            }
        }
    }
    
    // 3.3.4 버전의 콜백 메서드
    override fun onMessageDecryptionFailed(meshLayer: String?, errorMessage: String?) {
        // 메시지 복호화 실패 처리
        Log.e(TAG, "메시지 복호화 실패: $errorMessage")
    }
} 