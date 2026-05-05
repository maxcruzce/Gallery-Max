package com.hypergallery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypergallery.ui.theme.ChipActiveBg
import com.hypergallery.ui.theme.ChipInactive
import com.hypergallery.ui.theme.ChipInactiveText
import com.hypergallery.ui.theme.ChipText
import com.hypergallery.ui.components.MediaFilterChip

@Composable
fun FilterChips(
    selectedFilter: MediaFilterChip,
    onFilterSelected: (MediaFilterChip) -> Unit,
    modifier: Modifier = Modifier
) {
    val chips = listOf(
        MediaFilterChip.ALL to "Todas",
        MediaFilterChip.CAMERA to "Câmera",
        MediaFilterChip.FAVORITES to "Favoritas",
        MediaFilterChip.VIDEOS to "Vídeos",
        MediaFilterChip.SCREENSHOTS to "Capturas"
    )

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(chips) { (filter, label) ->
            Chip(
                text = label,
                isSelected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) }
            )
        }
    }
}

@Composable
private fun Chip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) ChipActiveBg else ChipInactive
    val textColor = if (isSelected) ChipText else ChipInactiveText

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
        )
    }
}