package com.hypergallery.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypergallery.data.MediaGroup
import com.hypergallery.ui.theme.Primary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.roundToInt

data class TimelineMarker(
    val year: String,
    val month: String?,
    val percentage: Float,
    val gridIndex: Int
)

@Composable
fun FastScroller(
    gridState: LazyGridState,
    mediaGroups: List<MediaGroup>,
    modifier: Modifier = Modifier
) {
    if (mediaGroups.isEmpty()) return

    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }
    var containerHeightPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    // Gerenciador de visibilidade (some após 2s)
    LaunchedEffect(isDragging, isVisible) {
        if (!isDragging && isVisible) {
            delay(2000)
            isVisible = false
        }
    }

    // Monitora o scroll real para mostrar a barra se necessário
    val isScrolling = gridState.isScrollInProgress
    LaunchedEffect(isScrolling) {
        if (isScrolling) {
            isVisible = true
        }
    }

    val timelineMarkers = remember(mediaGroups) {
        val markers = mutableListOf<TimelineMarker>()
        var currentIdx = 0
        val totalItems = mediaGroups.sumOf { it.items.size + 1 }
        if (totalItems == 0) return@remember emptyList<TimelineMarker>()

        val currentYear = Calendar.getInstance().get(Calendar.YEAR).toString()

        mediaGroups.forEach { group ->
            val parts = group.label.split(" ")
            val year = if (parts.size >= 5) parts[4] else if (parts.size >= 3) currentYear else ""
            val month = if (parts.size >= 3) parts[2].uppercase().take(3) else null
            
            if (year.isNotEmpty()) {
                val last = markers.lastOrNull()
                if (last == null || last.year != year || (month != null && last.month != month)) {
                    markers.add(TimelineMarker(year, month, currentIdx.toFloat() / totalItems, currentIdx))
                }
            }
            currentIdx += group.items.size + 1
        }
        markers
    }

    val scrollPercentage by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            if (totalItemsCount == 0) 0f
            else {
                val firstVisibleItemIndex = gridState.firstVisibleItemIndex
                (firstVisibleItemIndex.toFloat() / totalItemsCount).coerceIn(0f, 1f)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(80.dp) // Área lateral total
            .onGloballyPositioned { containerHeightPx = it.size.height.toFloat() }
    ) {
        if (containerHeightPx <= 0f) return@Box

        val handleHeight = 50.dp
        val handleHeightPx = with(density) { handleHeight.toPx() }
        val maxScrollRange = (containerHeightPx - handleHeightPx).coerceAtLeast(0f)
        val handleY = (scrollPercentage * maxScrollRange).coerceIn(0f, maxScrollRange.coerceAtLeast(0.001f))

        // ── ÁREA DE TOQUE AMPLIADA (Só consome se visível ou tentando arrastar) ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { 
                            isDragging = true
                            isVisible = true
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    ) { change, dragAmount ->
                        // Só consome o toque se o usuário estiver de fato interagindo com a lateral direita (80dp)
                        // E se o scroller estiver visível ou o arraste tenha começado
                        if (isVisible || isDragging) {
                            change.consume()
                            if (maxScrollRange > 0f) {
                                val currentY = scrollPercentage * maxScrollRange
                                val newY = (currentY + dragAmount.y).coerceIn(0f, maxScrollRange)
                                val newPercentage = newY / maxScrollRange
                                val totalItems = gridState.layoutInfo.totalItemsCount
                                val targetIndex = (newPercentage * totalItems).roundToInt()
                                scope.launch { gridState.scrollToItem(targetIndex) }
                            }
                        }
                    }
                }
        )

        // ── BOTÃO DE NAVEGAÇÃO (HANDLE) COM AUTO-HIDE ──
        val alpha by animateFloatAsState(
            targetValue = if (isVisible || isDragging) 1f else 0f,
            animationSpec = tween(500),
            label = "ScrollerAlpha"
        )

        if (alpha > 0f) {
            // ── TIMELINE RULER (Aparece ao arrastar) ──
            AnimatedVisibility(
                visible = isDragging,
                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
                modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd).padding(end = 40.dp).alpha(alpha)
            ) {
                Box(modifier = Modifier.fillMaxHeight()) {
                    timelineMarkers.forEach { marker ->
                        val markerY = marker.percentage * containerHeightPx
                        Column(
                            modifier = Modifier.offset { IntOffset(0, markerY.roundToInt()) },
                            horizontalAlignment = Alignment.End
                        ) {
                            if (marker.month == null || marker.month == "JAN") {
                                Text(
                                    text = marker.year,
                                    color = Color.Gray.copy(alpha = 0.8f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (marker.month != null) {
                                    Text(
                                        text = marker.month,
                                        color = Color.Gray.copy(alpha = 0.5f),
                                        fontSize = 7.sp,
                                        modifier = Modifier.padding(end = 2.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(width = 6.dp, height = 1.dp)
                                        .background(Color.Gray.copy(alpha = 0.3f))
                                )
                            }
                        }
                    }
                }
            }

            // ── HANDLE ──
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, handleY.roundToInt()) }
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp)
                    .size(28.dp, handleHeight)
                    .alpha(alpha)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDragging) Primary else Color.Gray.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.UnfoldMore,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // ── BUBBLE DE DATA (MAIS COMPACTO) ──
            AnimatedVisibility(
                visible = isDragging,
                enter = fadeIn() + scaleIn(transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)),
                exit = fadeOut() + scaleOut(transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { 
                        IntOffset(
                            x = with(density) { (-40).dp.toPx().roundToInt() },
                            y = handleY.roundToInt() + with(density) { 5.dp.toPx().roundToInt() }
                        )
                    }
                    .alpha(alpha)
            ) {
                val currentMarker = timelineMarkers.lastOrNull { it.percentage <= scrollPercentage }
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                    color = Primary,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentMarker?.month != null) {
                            Text(
                                text = currentMarker.month,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                        Text(
                            text = currentMarker?.year ?: "",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
