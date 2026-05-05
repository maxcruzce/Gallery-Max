package com.hypergallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import com.hypergallery.data.Album
import com.hypergallery.data.MediaGroup
import com.hypergallery.data.MediaItem
import com.hypergallery.ui.components.PhotoCell
import com.hypergallery.ui.components.FastScroller
import com.hypergallery.ui.theme.HeaderBg
import android.net.Uri
import androidx.compose.material3.TextButton
import com.hypergallery.ui.theme.NavBg
import com.hypergallery.ui.theme.OnSurface
import com.hypergallery.data.AlbumSort
import com.hypergallery.ui.theme.Primary
import com.hypergallery.ui.theme.Surface

@Composable
fun AlbumDetailScreen(
    album: Album,
    mediaGroups: List<MediaGroup>,
    isLoading: Boolean,
    selectedItems: Set<Long>,
    isSelectionMode: Boolean,
    initialScrollPosition: Int = 0,
    onBackClick: () -> Unit,
    onMediaClick: (MediaItem, scrollPosition: Int) -> Unit,
    onMediaLongClick: (MediaItem) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onSortSelected: (AlbumSort) -> Unit = {},
    onMoreClick: () -> Unit = {},
    selectingCoverForAlbumId: Long? = null,
    onSelectCover: (Uri) -> Unit = {},
    onCancelSelectCover: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()
    var showSortMenu by remember { mutableStateOf(false) }
    
    LaunchedEffect(initialScrollPosition, gridState) {
        if (initialScrollPosition > 0) {
            kotlinx.coroutines.delay(100)
            val totalItems = gridState.layoutInfo.totalItemsCount
            if (totalItems > 0) {
                val maxIndex = totalItems - 1
                val targetIndex = initialScrollPosition.coerceIn(0, maxIndex)
                gridState.scrollToItem(targetIndex)
            }
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Surface)
        ) {
            if (isSelectionMode) {
                SelectionHeader(
                    count = selectedItems.size,
                    onClose = onClearSelection,
                    onDelete = onDeleteSelected
                )
            } else if (selectingCoverForAlbumId != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Primary)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Selecione uma imagem para a capa",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = onCancelSelectCover) {
                            Text("CANCELAR", color = Color.White)
                        }
                    }
                }
            } else {
                AlbumDetailHeader(
                    title = album.name,
                    onBackClick = onBackClick
                )
            }

            if (isLoading && mediaGroups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary)
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        mediaGroups.forEach { group ->
                            item(span = { GridItemSpan(3) }) {
                                Text(
                                    text = group.label,
                                    color = OnSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                                )
                            }
                            items(group.items, key = { it.id }) { item ->
                                PhotoCell(
                                    item = item,
                                    isSelected = selectedItems.contains(item.id),
                                    isSelectionMode = isSelectionMode,
                                    onClick = { 
                                        if (selectingCoverForAlbumId != null) {
                                            onSelectCover(item.uri)
                                        } else {
                                            onMediaClick(item, gridState.firstVisibleItemIndex)
                                        }
                                    },
                                    onLongClick = { onMediaLongClick(item) }
                                )
                            }
                        }
                    }

                    // Navegação Rápida (Estilo Xiaomi)
                    if (mediaGroups.isNotEmpty() && !isSelectionMode) {
                        FastScroller(
                            gridState = gridState,
                            mediaGroups = mediaGroups,
                            modifier = Modifier.align(Alignment.CenterEnd).padding(top = 100.dp, bottom = 100.dp)
                        )
                    }
                }
            }
        }

        // Barra inferior direita (Pill de Ferramentas)
        if (!isSelectionMode && !isLoading) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(NavBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Organizar por",
                                tint = OnSurface
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Data (Mais Recentes)") },
                                onClick = { 
                                    onSortSelected(AlbumSort.DATE_DESC)
                                    showSortMenu = false 
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Data (Mais Antigas)") },
                                onClick = { 
                                    onSortSelected(AlbumSort.DATE_ASC)
                                    showSortMenu = false 
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Nome (A-Z)") },
                                onClick = { 
                                    onSortSelected(AlbumSort.NAME_ASC)
                                    showSortMenu = false 
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(onClick = onMoreClick) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Mais opções",
                            tint = OnSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionHeader(
    count: Int,
    onClose: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, "Fechar")
                }
                Text(
                    text = "$count selecionados",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Excluir")
            }
        }
    }
}

@Composable
private fun AlbumDetailHeader(
    title: String,
    onBackClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderBg)
            .padding(vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = OnSurface
                )
            }

            Text(
                text = title,
                color = OnSurface,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
