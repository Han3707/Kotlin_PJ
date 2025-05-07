package com.ssafy.lantern.ui.screens.call

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.lantern.R
import com.ssafy.lantern.data.model.FriendCallItem
import com.ssafy.lantern.ui.components.*
import com.ssafy.lantern.ui.components.CommonSearchBar
import com.ssafy.lantern.ui.theme.LanternTheme


@Composable
fun FriendListScreen(
    onBackClick: () -> Unit,
    onCallItemClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    paddingValues: PaddingValues = PaddingValues()
) {
    val dummyFriends = remember {
        listOf(
            FriendCallItem(1, "내가만든도깨비", R.drawable.lantern_image, "발신전화", "10:25 am", true),
            FriendCallItem(2, "내가만든도깨비", R.drawable.lantern_image, "발신전화", "10:25 am", true),
            FriendCallItem(3, "귀요미", R.drawable.lantern_image, "부재중전화", "10:20 am", true),
            FriendCallItem(4, "백성욱", R.drawable.lantern_image, "발신전화", "어제", false),
            FriendCallItem(5, "박인민", R.drawable.lantern_image, "부재중전화", "어제", false),
            FriendCallItem(6, "전세라1", R.drawable.lantern_image, "발신전화", "어제", false),
            FriendCallItem(7, "전세라2", R.drawable.lantern_image, "부재중전화", "어제", false),
            FriendCallItem(8, "김철수", R.drawable.lantern_image, "발신전화", "어제", false),
            FriendCallItem(9, "이영희", R.drawable.lantern_image, "부재중전화", "어제", false),
            FriendCallItem(10, "박지성", R.drawable.lantern_image, "발신전화", "2일 전", false),
            FriendCallItem(11, "손흥민", R.drawable.lantern_image, "부재중전화", "2일 전", false),
            FriendCallItem(12, "김민재", R.drawable.lantern_image, "발신전화", "2일 전", false),
            FriendCallItem(13, "황희찬", R.drawable.lantern_image, "부재중전화", "3일 전", false),
            FriendCallItem(14, "이강인", R.drawable.lantern_image, "발신전화", "3일 전", false),
            FriendCallItem(15, "조규성", R.drawable.lantern_image, "부재중전화", "3일 전", false)
        )
    }
    
    val (searchTerm, setSearchTerm) = remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .padding(paddingValues)
    ) {
        // Top Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(MaterialTheme.colors.background)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "최근 통화",
                color = MaterialTheme.colors.onBackground,
                style = MaterialTheme.typography.h4.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Common Search Bar
        CommonSearchBar(
            value = searchTerm,
            onValueChange = setSearchTerm
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Friend List with Scroll
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp)
        ) {
            items(dummyFriends) { friend ->
                Box(
                    modifier = Modifier
                        .clickable { onCallItemClick() }
                ) {
                    FriendCallItem(friend)
                }
                Divider(
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FriendListScreenPreview() {
    LanternTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background
        ) {
            FriendListScreen(
                onBackClick = {},
                onCallItemClick = {},
                onProfileClick = {}
            )
        }
    }
}
