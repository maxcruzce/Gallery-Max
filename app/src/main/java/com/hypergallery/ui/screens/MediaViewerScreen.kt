package com.hypergallery.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Speed
import com.hypergallery.ui.theme.Primary
import com.hypergallery.data.ExifData
import android.graphics.Rect
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.ButtonDefaults
import com.hypergallery.data.FaceInfo
import com.hypergallery.data.ImageMetadata
import com.hypergallery.data.MediaItem
import com.hypergallery.ui.components.FaceThumbnail
import com.hypergallery.ui.components.VideoPlayer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MediaViewerScreen(
    mediaList: List<MediaItem>,
    startIndex: Int,
    onBackClick: (currentIndex: Int) -> Unit,
    onDeleteClick: (MediaItem) -> Unit,
    onFavoriteClick: (MediaItem) -> Unit,
    onInfoClick: (MediaItem) -> Unit,
    onDetailsClick: (MediaItem) -> Unit,
    onGetMetadata: (Long) -> ImageMetadata = { ImageMetadata(it) },
    onGetExifData: (android.net.Uri) -> ExifData? = { null },
    onGetFaces: (Long) -> Unit = {},
    onSaveMetadata: (Long, String, List<String>) -> Unit = { _, _, _ -> },
    onRenameMedia: (MediaItem, String) -> Unit = { _, _ -> },
    onExtractText: (MediaItem, (String) -> Unit) -> Unit = { _, _ -> },
    onEditMedia: (MediaItem) -> Unit = {},
    onAdjustDocument: (MediaItem) -> Unit = {},
    onStartManualFaceSelection: () -> Unit = {},
    onCancelManualFaceSelection: () -> Unit = {},
    onUpdateFaceBounds: (String, Rect) -> Unit = { _, _ -> },
    onAlbumClick: (MediaItem) -> Unit = {},
    onPersonClick: (String) -> Unit = {},
    isManualFaceSelectionMode: Boolean = false,
    isViewingFromPerson: Boolean = false,
    faces: List<Triple<com.hypergallery.data.FaceInfo, String?, String?>> = emptyList(),
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(initialPage = startIndex) {
        mediaList.size
    }

    var showPlayer by remember { mutableStateOf(false) }
    var showBottomBar by remember { mutableStateOf(true) }
    var detailsExpanded by remember { mutableStateOf(false) }
    
    var showMoreMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<MediaItem?>(null) }
    var extractedText by remember { mutableStateOf<String?>(null) }
    var isExtracting by remember { mutableStateOf(false) }
    
    var isLooping by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    
    var videoPosition by remember { mutableLongStateOf(0L) }
    var videoDuration by remember { mutableLongStateOf(0L) }
    var isVideoPlaying by remember { mutableStateOf(true) }
    
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    
    val swipeThreshold = 300f
    
    val currentItem = mediaList.getOrNull(pagerState.currentPage)

    // Auto-hide para controles de vídeo (3s)
    LaunchedEffect(isVideoPlaying, showBottomBar, currentItem) {
        if (isVideoPlaying && showBottomBar && currentItem?.isVideo == true) {
            kotlinx.coroutines.delay(3000)
            showBottomBar = false
        }
    }
    
    LaunchedEffect(pagerState.currentPage) {
        currentItem?.let { onGetFaces(it.id) }
    }

    LaunchedEffect(isVideoPlaying) {
        if (isVideoPlaying && currentItem?.isVideo == true) {
            kotlinx.coroutines.delay(500)
            showBottomBar = false
        }
    }

    val animatedOffsetY by animateFloatAsState(
        targetValue = if (isDragging) dragOffsetY else 0f,
        animationSpec = spring(stiffness = if (isDragging) 300f else 500f),
        label = "dragOffset"
    )
    
    val swipeProgress = (abs(animatedOffsetY) / 600f).coerceIn(0f..1f)
    val swipeScale = (1f - swipeProgress * 0.8f).coerceIn(0.4f..1f)

    LaunchedEffect(pagerState.currentPage) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        dragOffsetY = 0f
        rotation = 0f
        showPlayer = false
        isLooping = false
        playbackSpeed = 1f
        videoPosition = 0L
        videoDuration = 0L
        isVideoPlaying = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (detailsExpanded) 1f else (1f - swipeProgress * 1.5f).coerceIn(0f..1f)))
            .then(
                // Gestos desativados se houver ZOOM, ROTAÇÃO ou DETALHES EXPANDIDOS
                if (scale == 1f && rotation == 0f && !detailsExpanded) {
                    Modifier.pointerInput(detailsExpanded) {
                        awaitEachGesture {
                            var verticalStarted = false
                            var horizontalStarted = false
                            var totalY = 0f
                            var totalX = 0f

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break

                                if (event.type == PointerEventType.Move) {
                                    val dx = change.position.x - change.previousPosition.x
                                    val dy = change.position.y - change.previousPosition.y
                                    totalX += dx
                                    totalY += dy

                                    if (!verticalStarted && !horizontalStarted) {
                                        // Se o movimento for predominantemente vertical, assume o controle
                                        if (abs(totalY) > 30f && abs(totalY) > abs(totalX)) {
                                            verticalStarted = true
                                        } else if (abs(totalX) > 30f) {
                                            // Se for horizontal, deixa o HorizontalPager trabalhar
                                            horizontalStarted = true
                                        }
                                    }

                                    if (verticalStarted) {
                                        change.consume()
                                        dragOffsetY = totalY
                                        isDragging = true
                                    }
                                } else if (event.type == PointerEventType.Release) {
                                    if (verticalStarted) {
                                        if (detailsExpanded) {
                                            if (dragOffsetY > swipeThreshold) {
                                                detailsExpanded = false
                                                onBackClick(pagerState.currentPage)
                                            } else if (dragOffsetY > 200f) {
                                                detailsExpanded = false
                                            }
                                        } else {
                                            if (dragOffsetY > swipeThreshold) {
                                                onBackClick(pagerState.currentPage)
                                            } else if (dragOffsetY < -swipeThreshold && currentItem != null) {
                                                detailsExpanded = true
                                            }
                                        }
                                    }
                                    isDragging = false
                                    dragOffsetY = 0f
                                    break
                                }
                            }
                        }
                    }
                } else Modifier
            )
            .clickable(enabled = !detailsExpanded) { showBottomBar = !showBottomBar }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (detailsExpanded) 0.6f else 1f)
                    .offset { IntOffset(0, animatedOffsetY.toInt()) }
                    .graphicsLayer {
                        scaleX = swipeScale
                        scaleY = swipeScale
                        alpha = if (detailsExpanded) 0.9f else (1f - swipeProgress * 1.5f).coerceIn(0f..1f)
                    }
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    pageSpacing = 0.dp,
                    userScrollEnabled = scale == 1f
                ) { page ->
                    val item = mediaList[page]
                    val isCurrentPage = page == pagerState.currentPage
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(isCurrentPage, detailsExpanded, scale) {
                                if (isCurrentPage && !detailsExpanded) {
                                    awaitEachGesture {
                                        do {
                                            val event = awaitPointerEvent()
                                            // Só intercepta se já houver zoom, rotação ou se houver mais de um dedo (pinça)
                                            // Isso permite que o HorizontalPager receba o swipe horizontal de um dedo quando scale == 1
                                            if (scale > 1f || rotation != 0f || event.changes.size > 1) {
                                                val zoomChange = event.calculateZoom()
                                                val panChange = event.calculatePan()
                                                val rotationChange = event.calculateRotation()

                                                if (zoomChange != 1f || panChange != androidx.compose.ui.geometry.Offset.Zero || rotationChange != 0f) {
                                                    val newScale = (scale * zoomChange).coerceIn(1f..8f)
                                                    if (newScale != scale) scale = newScale

                                                    rotation += rotationChange

                                                    if (scale > 1f || rotation != 0f) {
                                                        offsetX += panChange.x
                                                        offsetY += panChange.y
                                                        event.changes.forEach { it.consume() }
                                                    } else {
                                                        scale = 1f
                                                        offsetX = 0f
                                                        offsetY = 0f
                                                        rotation = 0f
                                                    }
                                                }
                                            }
                                        } while (event.changes.any { it.pressed })
                                    }
                                }
                            }
                            .pointerInput(isCurrentPage, detailsExpanded, scale) {
                                if (isCurrentPage && !detailsExpanded && scale > 1f) {
                                    detectDragGestures(
                                        onDragEnd = { },
                                        onDragCancel = { }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        offsetX += dragAmount.x
                                        offsetY += dragAmount.y
                                    }
                                }
                            }
                            .pointerInput(isCurrentPage, detailsExpanded) {
                                if (isCurrentPage && !detailsExpanded) {
                                    detectTapGestures(
                                        onDoubleTap = { offset ->
                                            scale = when {
                                                scale >= 4f -> 1f
                                                scale >= 2f -> 4f
                                                else -> 2f
                                            }
                                            if (scale == 1f) {
                                                offsetX = 0f
                                                offsetY = 0f
                                                rotation = 0f
                                            } else {
                                                offsetX = -offset.x + size.width / 2
                                                offsetY = -offset.y + size.height / 2
                                            }
                                        }
                                    )
                                }
                            }
                            .graphicsLayer {
                                scaleX = if (isCurrentPage) scale else 1f
                                scaleY = if (isCurrentPage) scale else 1f
                                translationX = if (isCurrentPage) offsetX else 0f
                                translationY = if (isCurrentPage) offsetY else 0f
                                rotationZ = if (isCurrentPage) rotation else 0f
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.isVideo && showPlayer && isCurrentPage) {
                            VideoPlayer(
                                videoUri = item.uri,
                                isLooping = isLooping,
                                playbackSpeed = playbackSpeed,
                                isPlaying = isVideoPlaying,
                                onProgress = { pos, dur ->
                                    videoPosition = pos
                                    videoDuration = dur
                                },
                                onTap = { showBottomBar = !showBottomBar }
                            )
                            
                            if (showBottomBar) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.3f))
                                        .clickable { isVideoPlaying = !isVideoPlaying },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(50.dp)
                                    )
                                }
                            }
                        } else {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = item.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )

                            if (item.isVideo && !showPlayer) {
                                IconButton(
                                    onClick = { showPlayer = true },
                                    modifier = Modifier.size(80.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Reproduzir",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(80.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = detailsExpanded,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f)
            ) {
                currentItem?.let { item ->
                    DetailsBottomSheet(
                        mediaItem = item,
                        metadata = onGetMetadata(item.id),
                        exifData = onGetExifData(item.uri),
                        faces = faces,
                        onSaveMetadata = { desc, tagList -> onSaveMetadata(item.id, desc, tagList) },
                        onAlbumClick = { onAlbumClick(item) },
                        onPersonClick = onPersonClick,
                        onClose = { detailsExpanded = false }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !detailsExpanded && showBottomBar && scale == 1f,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(top = 40.dp, start = 8.dp, end = 8.dp, bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onBackClick(pagerState.currentPage) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = Color.White
                        )
                    }
                    
                    Text(
                        text = "${pagerState.currentPage + 1} / ${mediaList.size}",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    
                    IconButton(onClick = { detailsExpanded = true }) {
                        Icon(Icons.Default.DragHandle, "Detalhes", tint = Color.White)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !detailsExpanded && showBottomBar && scale == 1f,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            currentItem?.let { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    // Barra de Progresso do Vídeo
                    if (item.isVideo && showPlayer && videoDuration > 0) {
                        Slider(
                            value = videoPosition.toFloat(),
                            onValueChange = { /* Implementar seek se necessário */ },
                            valueRange = 0f..videoDuration.toFloat(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Primary,
                                activeTrackColor = Primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onEditMedia(item) }) {
                            Icon(Icons.Default.Edit, "Editar", tint = Color.White)
                        }
                        
                        IconButton(onClick = { onFavoriteClick(item) }) {
                            Icon(
                                if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                "Favoritar",
                                tint = if (item.isFavorite) Color.Red else Color.White
                            )
                        }

                        IconButton(onClick = { onDeleteClick(item) }) {
                            Icon(Icons.Default.Delete, "Excluir", tint = Color.White)
                        }

                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Default.MoreVert, "Mais", tint = Color.White)
                            }
                            
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                modifier = Modifier.background(Color.White)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Adicionar a um Álbum") },
                                    onClick = { showMoreMenu = false },
                                    leadingIcon = { Icon(Icons.Default.DriveFileMove, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Editar") },
                                    onClick = { onEditMedia(item); showMoreMenu = false },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                                )
                                if (!item.isVideo) {
                                    DropdownMenuItem(
                                        text = { Text("Ajustar Documento") },
                                        onClick = { onAdjustDocument(item); showMoreMenu = false },
                                        leadingIcon = { Icon(Icons.Default.AutoAwesome, null) }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Renomear") },
                                    onClick = { showRenameDialog = item; showMoreMenu = false },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                                )
                                if (!item.isVideo && isViewingFromPerson) {
                                    DropdownMenuItem(
                                        text = { Text("Ajustar Rosto") },
                                        onClick = { onStartManualFaceSelection(); showMoreMenu = false },
                                        leadingIcon = { Icon(Icons.Default.Face, null) }
                                    )
                                }
                                if (item.isVideo) {
                                    DropdownMenuItem(
                                        text = { Text(if (isLooping) "Desativar Loop" else "Ativar Loop") },
                                        onClick = { isLooping = !isLooping; showMoreMenu = false },
                                        leadingIcon = { Icon(Icons.Default.Repeat, null, tint = if (isLooping) Primary else Color.Black) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Velocidade: ${playbackSpeed}x") },
                                        onClick = { showSpeedDialog = true; showMoreMenu = false },
                                        leadingIcon = { Icon(Icons.Default.Speed, null) }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Extrair Texto (IA)") },
                                    onClick = { 
                                        isExtracting = true
                                        onExtractText(item) { text ->
                                            extractedText = text
                                            isExtracting = false
                                        }
                                        showMoreMenu = false 
                                    },
                                    leadingIcon = { Icon(Icons.Default.TextSnippet, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Detalhes") },
                                    onClick = { detailsExpanded = true; showMoreMenu = false },
                                    leadingIcon = { Icon(Icons.Default.CameraAlt, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Apagar", color = Color.Red) },
                                    onClick = { onDeleteClick(item); showMoreMenu = false },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Dialogs
    showRenameDialog?.let { item ->
        var newName by remember { mutableStateOf(item.name.substringBeforeLast(".")) }
        val extension = item.name.substringAfterLast(".", "")
        
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Renomear Arquivo") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Novo nome") },
                    suffix = { if (extension.isNotEmpty()) Text(".$extension") else null },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { 
                    val finalName = if (extension.isNotEmpty()) "$newName.$extension" else newName
                    onRenameMedia(item, finalName)
                    showRenameDialog = null 
                }) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) { Text("Cancelar") }
            }
        )
    }
    
    extractedText?.let { text ->
        AlertDialog(
            onDismissRequest = { extractedText = null },
            title = { Text("Texto Extraído") },
            text = {
                Column {
                    Text("Você pode selecionar e copiar o texto abaixo:", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            text = text.ifEmpty { "Nenhum texto identificado na imagem." },
                            modifier = Modifier
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { extractedText = null }) { Text("Fechar") }
            }
        )
    }
    
    if (isExtracting) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = { },
            title = { Text("Analisando Imagem...") },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = Primary)
                }
            }
        )
    }

    if (showSpeedDialog) {
        // ... dialog code ...
    }

    if (isManualFaceSelectionMode && currentItem != null) {
        FaceAdjustmentOverlay(
            faces = faces,
            onConfirm = { faceId, newBounds -> onUpdateFaceBounds(faceId, newBounds) },
            onCancel = onCancelManualFaceSelection
        )
    }
}

@Composable
fun FaceAdjustmentOverlay(
    faces: List<Triple<com.hypergallery.data.FaceInfo, String?, String?>>,
    onConfirm: (String, Rect) -> Unit,
    onCancel: () -> Unit
) {
    var selectedFace by remember { mutableStateOf<com.hypergallery.data.FaceInfo?>(faces.firstOrNull()?.first) }
    var currentBounds by remember(selectedFace) { 
        mutableStateOf(selectedFace?.bounds ?: Rect(100, 100, 300, 300)) 
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f))) {
        // Área de Ajuste
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        currentBounds = Rect(
                            (currentBounds.left + dragAmount.x.toInt()).coerceAtLeast(0),
                            (currentBounds.top + dragAmount.y.toInt()).coerceAtLeast(0),
                            (currentBounds.right + dragAmount.x.toInt()),
                            (currentBounds.bottom + dragAmount.y.toInt())
                        )
                    }
                }
        ) {
            // Desenha o Retângulo Atual
            Box(
                modifier = Modifier
                    .offset(currentBounds.left.dp, currentBounds.top.dp)
                    .size((currentBounds.right - currentBounds.left).dp, (currentBounds.bottom - currentBounds.top).dp)
                    .border(2.dp, Primary, RoundedCornerShape(4.dp))
            )
        }

        // Controles
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Arraste para ajustar o rosto", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) {
                    Text("Cancelar")
                }
                Button(onClick = { 
                    selectedFace?.let { onConfirm(it.faceId, currentBounds) }
                }) {
                    Text("Confirmar")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsBottomSheet(
    mediaItem: MediaItem,
    metadata: ImageMetadata,
    exifData: ExifData?,
    faces: List<Triple<com.hypergallery.data.FaceInfo, String?, String?>> = emptyList(),
    onSaveMetadata: (String, List<String>) -> Unit,
    onAlbumClick: () -> Unit,
    onPersonClick: (String) -> Unit,
    onClose: () -> Unit
) {
    val scrollState = rememberScrollState()
    val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val fullDateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf(metadata.description) }
    var tags by remember { mutableStateOf(metadata.tags) }
    var newTag by remember { mutableStateOf("") }
    var showTagInput by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        if (dragOffset > 80) {
                            onSaveMetadata(description, tags)
                            onClose()
                        }
                        dragOffset = 0f
                    },
                    onDragCancel = {
                        isDragging = false
                        dragOffset = 0f
                    }
                ) { change, dragAmount ->
                    change.consume()
                    if (isDragging) {
                        dragOffset += dragAmount
                    }
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { 
                onSaveMetadata(description, tags)
                onClose()
            }) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Fechar detalhes",
                    tint = Color.Gray
                )
            }
            
            Text(
                text = "Detalhes",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            
            IconButton(onClick = { 
                onSaveMetadata(description, tags)
                onClose()
            }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Fechar",
                    tint = Color.Black
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(bottom = 32.dp)
        ) {
            val displayDate = try {
                if (!exifData?.dateTime.isNullOrBlank()) {
                    val exifFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                    val exifDate = exifFormat.parse(exifData!!.dateTime)
                    exifDate?.let { dateFormatter.format(it) } ?: dateFormatter.format(Date(mediaItem.dateTaken))
                } else {
                    dateFormatter.format(Date(mediaItem.dateTaken))
                }
            } catch (e: Exception) {
                dateFormatter.format(Date(mediaItem.dateTaken))
            }

            Text(
                text = mediaItem.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (faces.isNotEmpty()) {
                Text(
                    text = "Pessoas na imagem",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    faces.take(5).forEach { (face, name, personId) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FaceThumbnail(
                                face = face,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape),
                                onClick = { personId?.let { onPersonClick(it) } }
                            )
                            Text(
                                text = name ?: "Desconhecido",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(60.dp).padding(top = 4.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFE0E0E0), modifier = Modifier.padding(vertical = 8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF2F1F6)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Descrição",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    BasicTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 13.sp),
                        decorationBox = { innerTextField ->
                            Box {
                                if (description.isEmpty()) {
                                    Text("Adicione uma descrição...", color = Color.Gray, fontSize = 13.sp)
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF2F1F6)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tag,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tags",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                        IconButton(onClick = { showTagInput = !showTagInput }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Adicionar tag",
                                tint = Color.Black
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (showTagInput) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newTag,
                                onValueChange = { newTag = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Nova tag", fontSize = 12.sp) },
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                singleLine = true
                            )
                            IconButton(
                                onClick = {
                                    if (newTag.isNotBlank() && !tags.contains(newTag.trim())) {
                                        tags = tags + newTag.trim()
                                        newTag = ""
                                        showTagInput = false
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Add, "Adicionar", tint = Color.Black)
                            }
                        }
                    }

                    if (tags.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tags.forEach { tag ->
                                Row(
                                    modifier = Modifier
                                        .background(Color.White, RoundedCornerShape(16.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = tag,
                                        color = Color.Black,
                                        fontSize = 12.sp
                                    )
                                    IconButton(
                                        onClick = { tags = tags - tag },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remover tag",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Toque + para adicionar tags",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF2F1F6)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (mediaItem.isVideo) Icons.Default.VideoFile else Icons.Default.Image,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Arquivo",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DetailColumn("Nome", mediaItem.name.take(15))
                        DetailColumn("Tamanho", formatFileSize(mediaItem.size))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DetailColumn("Dimensões", "${mediaItem.width}×${mediaItem.height}")
                        if (mediaItem.isVideo) {
                            DetailColumn("Duração", formatDuration(mediaItem.duration))
                        }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF2F1F6)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Câmera",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ExifChip("f/1.8", "Abertura")
                        ExifChip("1/60s", "Velocidade")
                        ExifChip("ISO 400", "ISO")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ExifChip("4.25mm", "Distância")
                        ExifChip("Não", "Flash")
                        ExifChip("Auto", "Modo")
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF2F1F6)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Data e hora",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val fullDisplayDate = try {
                        if (!exifData?.dateTime.isNullOrBlank()) {
                            val exifFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                            val exifDate = exifFormat.parse(exifData!!.dateTime)
                            exifDate?.let { fullDateFormatter.format(it) } ?: fullDateFormatter.format(Date(mediaItem.dateTaken))
                        } else {
                            fullDateFormatter.format(Date(mediaItem.dateTaken))
                        }
                    } catch (e: Exception) {
                        fullDateFormatter.format(Date(mediaItem.dateTaken))
                    }

                    Text(
                        text = fullDisplayDate,
                        color = Color.Black,
                        fontSize = 13.sp
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickable { onAlbumClick() },
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF2F1F6)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Álbum",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = mediaItem.bucketName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailColumn(label: String, value: String) {
    Column {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 11.sp
        )
        Text(
            text = value,
            color = Color.Black,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ExifChip(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Text(
            text = label,
            fontSize = 9.sp,
            color = Color.Gray
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> String.format(Locale.getDefault(), "%.2f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format(Locale.getDefault(), "%.2f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format(Locale.getDefault(), "%.2f KB", bytes / 1_000.0)
        else -> "$bytes bytes"
    }
}

private fun formatDuration(durationMs: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}