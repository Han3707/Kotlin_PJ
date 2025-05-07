package com.ssafy.lantern.data.database


import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ssafy.lantern.data.model.CallList
import com.ssafy.lantern.data.model.ChatRoom
import com.ssafy.lantern.data.model.Follow
import com.ssafy.lantern.data.model.Messages
import com.ssafy.lantern.data.model.User
import com.ssafy.lantern.data.repository.CallListDao
import com.ssafy.lantern.data.repository.ChatRoomDao
import com.ssafy.lantern.data.repository.FollowDao
import com.ssafy.lantern.data.repository.MessagesDao
import com.ssafy.lantern.data.repository.UserDao

@Database(
    entities = [
        User::class,
        CallList::class,
        Messages::class,
        Follow::class,
        ChatRoom::class
    ],
    version = 2,
    autoMigrations = [
        AutoMigration(from = 1, to = 2)
    ],// 버전 2로 업데이트
    exportSchema = true,             // 스키마 JSON 내보내기

)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun callListDao(): CallListDao
    abstract fun chatRoomDao(): ChatRoomDao
    abstract fun followDao(): FollowDao
    abstract fun messagesDao(): MessagesDao
}
