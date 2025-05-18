// 네비게이션 라우트 정의
object AppDestinations {
    const val SPLASH_ROUTE = "splash"
    const val LOGIN_ROUTE = "login"
    const val SIGNUP_ROUTE = "signup"
    const val MYPAGE_ROUTE = "mypage"
    const val FRIENDLIST_ROUTE = "friendlist"
    const val INCOMING_CALL_ROUTE = "incomingcall"
    const val ONGOING_CALL_ROUTE = "ongoingcall"
    const val OUTGOING_CALL_ROUTE = "outgoingcall/{receiverId}"
    const val OUTGOING_CALL_ARG_RECEIVER_ID = "receiverId"
    const val HOME_ROUTE = "home"
    const val MAIN_SCREEN_ROUTE = "main_screen"
    const val ONDEVICE_AI_ROUTE = "ondevice_ai"
    const val CALL_HISTORY_ROUTE = "call_history"

    const val PUBLIC_CHAT_ROUTE = "public_chat"
    const val DIRECT_CHAT_ROUTE = "direct_chat/{userId}"
    const val DIRECT_CHAT_ARG_USER_ID = "userId"
    const val DIRECT_CHAT_ARG_CHAT_ROOM_ID = "chatRoomId"

    const val PROFILE_ROUTE = "profile/{userId}/{name}/{distance}"
    const val PROFILE_ARG_USER_ID = "userId"
    const val PROFILE_ARG_NAME = "name"
    const val PROFILE_ARG_DISTANCE = "distance"
} 