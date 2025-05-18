package com.ssafy.lanterns.ui.screens.chat

import android.app.Activity
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ssafy.lanterns.utils.PermissionHelper
import com.ssafy.lanterns.service.ble.advertiser.AdvertiserManager
import com.ssafy.lanterns.service.ble.scanner.ScannerManager
import com.ssafy.lanterns.ui.components.ChatMessageBubble
import com.ssafy.lanterns.ui.components.ChatUser
import com.ssafy.lanterns.ui.components.NearbyUsersModal
import com.ssafy.lanterns.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import com.ssafy.lanterns.service.ble.GlobalBleManager
import com.ssafy.lanterns.ui.screens.main.MainViewModel
import kotlin.collections.isNotEmpty
import androidx.compose.runtime.collectAsState
import com.ssafy.lanterns.data.model.chat.ChatMessage
import androidx.compose.foundation.lazy.items

/**
 * BLE를 이용한 공용 채팅 구현 주석
 * 
 * 공용 채팅에서는 BLE를 통해 다음과 같은 기능을 구현할 수 있습니다:
 * 
 * 1. 광고(Advertising): 
 *    - 사용자 정보와 함께 광고 신호를 보내 주변에 자신의 존재를 알림
 *    - 사용자 ID, 이름, 프로필 이미지 정보 등을 페이로드에 포함
 * 
 * 2. 스캔(Scanning):
 *    - 주변의 광고 신호를 스캔하여 다른 사용자 탐색
 *    - 신호 강도(RSSI)를 통해 상대적 거리 계산
 *    - 스캔 결과를 NearbyUsersModal에 표시
 * 
 * 3. GATT 서버:
 *    - 메시지 특성(Characteristic)을 포함한 서비스 제공
 *    - 다른 기기가 연결하여 메시지를 주고받을 수 있도록 함
 * 
 * 4. GATT 클라이언트:
 *    - 탐색된 기기와 연결하여 메시지 교환
 *    - 연결된 모든 기기에 메시지 브로드캐스트 가능
 * 
 * 구현 아키텍처:
 * - 중앙 관리자 기기 없이 P2P 방식으로 통신
 * - 메시지 전송 시 연결된 모든 기기에 브로드캐스트
 * - 메시지는 임시 ID와 함께 전송하여 중복 수신 방지
 * - 사용자 접근성에 따라 메시지 필터링 가능 (거리 기반)
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicChatScreen(
    navController: NavController,
    paddingValues: PaddingValues = PaddingValues(0.dp),
    viewModel: PublicChatScreenViewModel = hiltViewModel()
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val shouldShowScrollToBottom by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
    var showUsersModal by remember { mutableStateOf(false) }
    var messageInput by remember { mutableStateOf("") }
    
    // 메시지 전송 가능 상태 (딜레이를 위한 상태 추가)
    var canSendMessage by remember { mutableStateOf(true) }
    
    // 주변 사용자 목록
    val nearbyUsers = remember { mutableStateListOf<ChatUser>() }
    
    val uiState by viewModel.uiState.collectAsState()
    
    // 객체 생성
    val context = LocalContext.current
    
    // 메인 화면의 BLE 서비스 제어를 위한 ViewModel 가져오기
    val mainViewModel = hiltViewModel<MainViewModel>()
    
    // 메시지 전송 함수 정의
    val sendMessage = {
        if (messageInput.isNotEmpty() && canSendMessage) {
            // 새 메시지 객체 생성
            val newMessageId = System.currentTimeMillis().toInt() // getNextMessageId 대신 타임스탬프 사용
            val chatMessage = ChatMessage(
                id = newMessageId,
                sender = "나", // 직접 지정
                text = messageInput,
                time = System.currentTimeMillis(),
                isMe = true,
                rssi = -1,
                isPublic = true
            )
            
            // 뷰모델에 메시지 추가
            viewModel.addMessage(chatMessage)
            
            // Activity 안전하게 추출
            val activity = context as? Activity
            if (activity != null) {
                // 개선된 GlobalBleManager를 통한 메시지 전송
                GlobalBleManager.sendChatMessage(
                    sender = "나", // 직접 지정
                    content = messageInput,
                    activity = activity
                )
            }
            
            // 메시지 입력 필드 초기화
            messageInput = ""
            
            // 전송 후 딜레이 설정
            canSendMessage = false
            coroutineScope.launch {
                delay(1500) // 1.5초 딜레이
                canSendMessage = true
            }
            
            // 스크롤 최하단으로
            coroutineScope.launch {
                delay(100)
                listState.animateScrollToItem(0)
            }
        }
    }
    
    // 채팅 화면 진입/종료 시 BLE 서비스 제어 - GlobalBleManager 사용
    DisposableEffect(Unit) {
        // BLE 관리자에 현재 화면 등록
        GlobalBleManager.setActiveScreen(
            screenType = GlobalBleManager.SCREEN_PUBLIC_CHAT,
            activity = context as Activity,
            chatListener = { sender, message, rssi ->
                // rssi 정보도 포함하도록 개선
                val isMyMessage = false // sender가 현재 사용자와 같은지 확인
                val newMessageId = System.currentTimeMillis().toInt() // 타임스탬프로 대체
                val rssiInt = rssi.toIntOrNull() ?: -127
                
                // 수신된 메시지 추가
                val chatMessage = ChatMessage(
                    id = newMessageId,
                    sender = sender,
                    text = message,
                    time = System.currentTimeMillis(),
                    isMe = isMyMessage,
                    rssi = rssiInt,
                    isPublic = true
                )
                
                viewModel.addMessage(chatMessage)
            }
        )
        
        // 초기 메시지 설정
        viewModel.initializeDefaultMessages()
        
        onDispose {
            // 별도 리소스 정리 불필요 (GlobalBleManager가 담당)
        }
    }
    
    LaunchedEffect(Unit) {
        // PermissionHelper 객체 생성
        val permissionHelper = PermissionHelper(context as Activity)
        
        // 이전 스캔과 광고 중지 (화면 재진입 시 초기화 위함)
        ScannerManager.stopScanning()
        AdvertiserManager.stopAdvertising()
        
        // 매니저 초기화
        ScannerManager.init(context as Activity)
        AdvertiserManager.init(context as Activity)

        // 권한이 없다면 요청
        if(!permissionHelper.hasPermission()) {
            permissionHelper.requestPermissions(1001)
        }
        // 있다면
        else {
            // 블루투스를 사용자가 켰는지 확인
            if(permissionHelper.isBluetoothEnabeld()) {
                // 스캔 시작 - 메시지와 사용자 정보 수신
                ScannerManager.startScanning(context as Activity, { sender, text, rssi ->
                    // 메시지 수신 처리
                    val newMessage = ChatMessage(
                        id = System.currentTimeMillis().toInt(), // 현재 시간을 ID로 사용
                        sender = sender,
                        text = text,
                        time = System.currentTimeMillis(),
                        isMe = false,
                        senderProfileId = 1, // 기본 프로필 ID
                        rssi = rssi.toIntOrNull() ?: -127,
                        isPublic = true
                    )
                    viewModel.addMessage(newMessage)
                    
                    // 발신자가 주변 사용자 목록에 없으면 추가
                    if (sender != "Unknown" && nearbyUsers.none { it.name == sender }) {
                        nearbyUsers.add(
                            ChatUser(
                                id = nearbyUsers.size + 1,
                                name = sender,
                                distance = 100f, // 기본 거리
                                messageCount = 1f // 기본 메시지 개수
                            )
                        )
                    }
                })
                Log.d("PublicChatScreen", "블루투스 스캔 시작 완료")
            }
            else {
                Log.d("PublicChatScreen", "블루투스가 활성화되지 않았습니다.")
            }
        }
    }

    // 메시지 스크롤 효과
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }
    
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .statusBarsPadding(), // systemBarsPadding() 대신 statusBarsPadding()만 적용
        contentColor = MaterialTheme.colorScheme.onBackground,
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 탑 앱바
                TopAppBar(
                    title = {
                        Text(
                            text = "모두의 광장",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "뒤로가기",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        // 참여자 수 아이콘
                        IconButton(onClick = { showUsersModal = true }) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.People,
                                    contentDescription = "참여자 목록",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    // 현재 사용자(나) + 다른 주변 사용자 표시
                                    text = (nearbyUsers.size + 1).toString(),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    scrollBehavior = scrollBehavior
                )
                
                // 메시지 목록
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 8.dp),
                    state = listState,
                    reverseLayout = true, // DirectChatScreen과 일관되게 설정
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(
                        items = uiState.messages,
                        key = { msg -> msg.id }
                    ) { msg ->
                        ChatMessageBubble(
                            senderName = if (msg.isMe) "나" else msg.sender,
                            text = msg.text,
                            time = msg.time,
                            isMe = msg.isMe,
                            senderProfileId = msg.senderProfileId ?: 1,
                            navController = navController,
                            chatBubbleColor = if (msg.isMe) ChatBubbleMine else ChatBubbleOthers,
                            textColor = MaterialTheme.colorScheme.onBackground,
                            metaTextColor = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                
                // 메시지 입력창 영역
                PublicChatInputRow(
                    message = messageInput,
                    onMessageChange = { messageInput = it },
                    onSendClick = { sendMessage() },
                    isSendEnabled = canSendMessage,
                    modifier = Modifier
                )
            }
            
            // 주변 사용자 목록 모달
            AnimatedVisibility(
                visible = showUsersModal,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                NearbyUsersModal(
                    users = nearbyUsers,
                    onDismiss = { showUsersModal = false },
                    navController = navController
                )
            }
        }
    }
}

@Composable
fun PublicChatInputRow(
    message: String,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSendEnabled: Boolean = true
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .navigationBarsPadding() // 내비게이션 바 패딩 유지
            .imePadding() // exclude 대신 단순 imePadding() 적용
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = message,
                onValueChange = onMessageChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("메시지 입력...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                trailingIcon = {
                    IconButton(
                        onClick = onSendClick,
                        enabled = message.isNotBlank() && isSendEnabled
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "전송",
                            tint = if (message.isNotBlank() && isSendEnabled) 
                                   MaterialTheme.colorScheme.secondary
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            )
        }
    }
}

// 타임스탬프를 "오전 10:30" 형식으로 변환하는 함수
private fun formatTime(timestamp: Long): String {
    val calendar = java.util.Calendar.getInstance().apply {
        timeInMillis = timestamp
    }
    val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
    val minute = calendar.get(java.util.Calendar.MINUTE)
    
    val amPm = if (hour < 12) "오전" else "오후"
    val hour12 = if (hour == 0 || hour == 12) 12 else hour % 12
    
    return "$amPm ${hour12}:${minute.toString().padStart(2, '0')}"
}