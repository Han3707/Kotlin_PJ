package com.ssafy.lanterns.data.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.migration.Migration
import androidx.room.RenameColumn
import androidx.room.RenameTable
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ssafy.lanterns.data.model.CallList
import com.ssafy.lanterns.data.model.ChatRoom
import com.ssafy.lanterns.data.model.Follow
import com.ssafy.lanterns.data.model.Message
import com.ssafy.lanterns.data.model.User
import com.ssafy.lanterns.data.source.local.dao.ChatRoomDao
import com.ssafy.lanterns.data.source.local.dao.MessageDao
import com.ssafy.lanterns.data.source.local.dao.CallListDao
import com.ssafy.lanterns.data.source.local.dao.FollowDao
import com.ssafy.lanterns.data.source.local.dao.UserDao

// 버전 2에서 버전 3으로의 수동 마이그레이션
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 1. chat_room 테이블을 chat_rooms 테이블로 변경
        database.execSQL("ALTER TABLE chat_room RENAME TO chat_rooms")
        
        // 2. chat_rooms 테이블에 새로운 컬럼 추가
        database.execSQL("ALTER TABLE chat_rooms ADD COLUMN participantNickname TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE chat_rooms ADD COLUMN participantProfileImageNumber INTEGER NOT NULL DEFAULT 1")
        database.execSQL("ALTER TABLE chat_rooms ADD COLUMN lastMessage TEXT DEFAULT NULL")
        
        // 3. chat_room_id 컬럼 타입이 INTEGER에서 TEXT로 변경 필요
        // SQLite에서는 컬럼 타입 변경이 직접 지원되지 않으므로 테이블을 새로 만들고 데이터를 이동해야 함
        database.execSQL("""
            CREATE TABLE new_chat_rooms (
                chatRoomId TEXT NOT NULL PRIMARY KEY,
                participantId INTEGER NOT NULL DEFAULT 0,
                participantNickname TEXT NOT NULL DEFAULT '',
                participantProfileImageNumber INTEGER NOT NULL DEFAULT 1,
                lastMessage TEXT,
                updatedAt TEXT NOT NULL
            )
        """)
        
        // 기존 데이터 복사 (chatRoomId를 문자열로 변환)
        database.execSQL("""
            INSERT INTO new_chat_rooms (chatRoomId, participantId, updatedAt)
            SELECT chat_room_id, participant_id, updated_at FROM chat_rooms
        """)
        
        // 이전 테이블 삭제
        database.execSQL("DROP TABLE chat_rooms")
        
        // 새 테이블 이름 변경
        database.execSQL("ALTER TABLE new_chat_rooms RENAME TO chat_rooms")
        
        // 4. messages 테이블 재생성
        database.execSQL("""
            CREATE TABLE new_messages (
                messageId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL DEFAULT 0,
                chatRoomId TEXT NOT NULL DEFAULT '',
                senderId INTEGER NOT NULL DEFAULT 0,
                receiverId INTEGER NOT NULL DEFAULT 0,
                content TEXT NOT NULL DEFAULT '',
                timestamp INTEGER NOT NULL,
                isSentByMe INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT '0',
                messageType TEXT NOT NULL DEFAULT 'TEXT',
                FOREIGN KEY(chatRoomId) REFERENCES chat_rooms(chatRoomId) ON DELETE CASCADE
            )
        """)
        
        // 기존 데이터 복사 시도 (필드 이름 변경 및 새 필드에 기본값 적용)
        try {
            database.execSQL("""
                INSERT INTO new_messages (chatRoomId, senderId, content, timestamp)
                SELECT chat_room_id, user_id, text, date FROM messages
            """)
        } catch (e: Exception) {
            // 데이터 복사 실패 시 기존 데이터 없이 진행
        }
        
        // 이전 테이블 삭제
        database.execSQL("DROP TABLE messages")
        
        // 새 테이블 이름 변경
        database.execSQL("ALTER TABLE new_messages RENAME TO messages")
        
        // 필요한 인덱스 생성
        database.execSQL("CREATE INDEX IF NOT EXISTS index_messages_chatRoomId ON messages(chatRoomId)")
    }
}

@Database(
    entities = [
        User::class,
        CallList::class,
        Message::class,
        Follow::class,
        ChatRoom::class
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun callListDao(): CallListDao
    abstract fun chatRoomDao(): ChatRoomDao
    abstract fun followDao(): FollowDao
    abstract fun messageDao(): MessageDao

    companion object {
        const val DATABASE_NAME = "lanterns-db"
        
        // Room 데이터베이스 빌더를 위한 도우미 함수
        fun buildDatabase(context: android.content.Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
            // .addMigrations(MIGRATION_2_3) // 수동 마이그레이션 추가 (주석 처리)
            // 마이그레이션을 포기하고 데이터베이스를 새로 만듦 (개발 중에만 권장)
            .fallbackToDestructiveMigration()
            .build()
        }
    }
}
