package com.ssafy.lanterns.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "call_list")
data class CallList(
    @PrimaryKey
    @ColumnInfo(name = "call_id")
    val callId : Long,

    @ColumnInfo(name = "user_id")
    val userId : Long,

    val duration: Int,

    val date: LocalDateTime,

    @ColumnInfo(name = "incoming_outgoing")
    val incomingOutgoing: Boolean
)
