package com.ssafy.lantern.service.network

import android.util.Log
import java.util.UUID

/**
 * 메시 네트워크 정보를 저장하는 클래스
 * 네트워크 키, 애플리케이션 키 등의 정보 관리
 */
data class MeshNetwork(
    // 네트워크 키 목록 (UUID 값으로 관리)
    val netKeys: List<UUID> = emptyList(),
    
    // 애플리케이션 키 목록 (UUID 값으로 관리)
    val appKeys: List<UUID> = emptyList(),
    
    // 로컬 주소
    val localAddress: Int = 0,
    
    // 프로비저닝된 노드 주소 목록
    val provisionedNodes: List<Int> = emptyList()
) {
    companion object {
        private const val TAG = "MeshNetwork"
    }
    
    /**
     * 키 유무를 확인하는 함수
     */
    fun hasKeys(): Boolean {
        return netKeys.isNotEmpty() && appKeys.isNotEmpty()
    }
    
    /**
     * 네트워크 키 추가
     */
    fun withNetworkKey(key: UUID): MeshNetwork {
        val updatedKeys = netKeys.toMutableList()
        if (!updatedKeys.contains(key)) {
            updatedKeys.add(key)
            Log.d(TAG, "네트워크 키 추가: $key")
        }
        return copy(netKeys = updatedKeys)
    }
    
    /**
     * 앱 키 추가
     */
    fun withApplicationKey(key: UUID): MeshNetwork {
        val updatedKeys = appKeys.toMutableList()
        if (!updatedKeys.contains(key)) {
            updatedKeys.add(key)
            Log.d(TAG, "애플리케이션 키 추가: $key")
        }
        return copy(appKeys = updatedKeys)
    }
    
    /**
     * 프로비저닝된 노드 추가
     */
    fun withProvisionedNode(address: Int): MeshNetwork {
        val updatedNodes = provisionedNodes.toMutableList()
        if (!updatedNodes.contains(address)) {
            updatedNodes.add(address)
            Log.d(TAG, "프로비저닝된 노드 추가: $address")
        }
        return copy(provisionedNodes = updatedNodes)
    }
} 