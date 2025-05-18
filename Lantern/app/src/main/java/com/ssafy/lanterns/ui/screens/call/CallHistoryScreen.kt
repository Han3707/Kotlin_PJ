package com.ssafy.lanterns.ui.screens.call

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.ssafy.lanterns.R
import com.ssafy.lanterns.data.model.FriendCallItem
import com.ssafy.lanterns.ui.components.*
import com.ssafy.lanterns.ui.navigation.AppDestinations
import com.ssafy.lanterns.ui.theme.*
import androidx.compose.material3.MaterialTheme

/**
 * 통화 기록 화면
 */
@Composable
fun CallHistoryScreen(
    navController: NavController,
    paddingValues: PaddingValues = PaddingValues(),
    onCallItemClick: (Int) -> Unit = {}
) {
    val callHistory = remember {
        listOf(
            FriendCallItem(1, "도경원", R.drawable.lantern_image, "발신전화", "10:25 am", true),
            FriendCallItem(2, "김준호", R.drawable.lantern_image, "수신전화", "10:25 am", true),
            FriendCallItem(3, "박지민", R.drawable.lantern_image, "부재중전화", "10:20 am", true),
            FriendCallItem(4, "이승우", R.drawable.lantern_image, "발신전화", "어제", false),
            FriendCallItem(5, "최유진", R.drawable.lantern_image, "부재중전화", "어제", false),
            FriendCallItem(6, "정지원", R.drawable.lantern_image, "수신전화", "어제", false),
            FriendCallItem(7, "한소희", R.drawable.lantern_image, "부재중전화", "어제", false),
            FriendCallItem(8, "윤하준", R.drawable.lantern_image, "발신전화", "2일 전", false),
            FriendCallItem(9, "서민정", R.drawable.lantern_image, "수신전화", "2일 전", false),
            FriendCallItem(10, "장현우", R.drawable.lantern_image, "발신전화", "3일 전", false)
        )
    }
    
    val (searchTerm, setSearchTerm) = remember { mutableStateOf("") }
    val filteredCalls = remember(searchTerm, callHistory) {
        if (searchTerm.isBlank()) {
            callHistory
        } else {
            callHistory.filter { 
                it.name.contains(searchTerm, ignoreCase = true) 
            }
        }
    }

    val groupedCalls = remember(filteredCalls) {
        filteredCalls.groupBy { call -> 
            when {
                call.isRecent -> "오늘"
                call.timestamp.contains("어제") -> "어제"
                call.timestamp.contains("일 전") -> "최근"
                else -> "이전"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(paddingValues)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp)
        ) {
            Text(
                text = "통화 기록",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.25).sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 13.dp)
            ) {
                SearchBar(
                    value = searchTerm,
                    onValueChange = setSearchTerm,
                    placeholderText = "이름 검색..."
                )
            }
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                groupedCalls.forEach { (date, calls) ->
                    item {
                        Text(
                            text = date,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                        )
                    }
                    
                    items(calls) { call ->
                        CallHistoryItem(
                            call = call,
                            onClick = { onCallItemClick(call.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholderText: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { 
                Text(
                    text = placeholderText,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            },
            leadingIcon = { 
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "검색",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp)
                )
            },
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "지우기",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                lineHeight = 24.sp
            ),
            singleLine = true
        )
    }
}

@Composable
fun CallHistoryItem(
    call: FriendCallItem,
    onClick: () -> Unit
) {
    val callTypeColor = when (call.callType) {
        "부재중전화" -> CallHistoryMissingCall
        "발신전화" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileAvatar(
                profileId = call.id,
                name = call.name,
                size = 40.dp,
                hasBorder = true,
                borderColor = callTypeColor.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = call.name,
                    fontWeight = FontWeight.Bold,
                    color = if (call.callType == "부재중전화") callTypeColor else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (call.callType) {
                            "발신전화" -> Icons.Default.CallMade
                            "수신전화" -> Icons.Default.CallReceived
                            else -> Icons.Default.CallMissed
                        },
                        contentDescription = call.callType,
                        tint = callTypeColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = call.callType,
                        color = callTypeColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                text = call.timestamp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultCallHistoryScreenPreview() {
    LanternsTheme {
        CallHistoryScreen(navController = rememberNavController())
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultCallHistoryItemPreview() {
    val call = FriendCallItem(1, "김싸피", R.drawable.lantern_image, "부재중전화", "10:25 am", true)
    LanternsTheme {
        CallHistoryItem(call = call, onClick = {})
    }
} 