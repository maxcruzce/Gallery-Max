package com.hypergallery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypergallery.ui.theme.ChipInactiveText
import com.hypergallery.ui.theme.NavBg
import com.hypergallery.ui.theme.OnSurface
import com.hypergallery.ui.theme.SurfaceContainer

enum class GalleryTab { PHOTOS, ALBUMS }

@Composable
fun NavigationPill(
    selectedTab: GalleryTab,
    onTabSelected: (GalleryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(40.dp))
                .background(NavBg)
                .padding(vertical = 8.dp, horizontal = 8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NavTab(
                    icon = Icons.Filled.PhotoLibrary,
                    label = "Fotos",
                    isSelected = selectedTab == GalleryTab.PHOTOS,
                    onClick = { onTabSelected(GalleryTab.PHOTOS) }
                )
                NavTab(
                    icon = Icons.Outlined.Collections,
                    label = "Álbuns",
                    isSelected = selectedTab == GalleryTab.ALBUMS,
                    onClick = { onTabSelected(GalleryTab.ALBUMS) }
                )
            }
        }
    }
}

@Composable
private fun NavTab(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val iconColor = if (isSelected) OnSurface else Color(0xFFb0b0b8)
    val textColor = if (isSelected) OnSurface else Color(0xFFb0b0b8)

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}