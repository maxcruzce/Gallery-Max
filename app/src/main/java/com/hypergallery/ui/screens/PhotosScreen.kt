package com.hypergallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypergallery.data.MediaGroup
import com.hypergallery.data.MediaItem
import com.hypergallery.ui.components.FilterChips
import com.hypergallery.ui.components.MediaFilterChip
import com.hypergallery.ui.components.PhotoCell
import com.hypergallery.ui.components.FastScroller
import com.hypergallery.ui.theme.HeaderBg
import com.hypergallery.ui.theme.OnSurface
import com.hypergallery.ui.theme.Primary
import com.hypergallery.ui.theme.Surface
import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotosScreen(
    mediaGroups: List<MediaGroup>,
    isLoading: Boolean,
    selectedFilter: MediaFilterChip,
    selectedItems: Set<Long>,
    isSelectionMode: Boolean,
    initialScrollPosition: Int = -1,
    searchSuggestions: List<String> = emptyList(),
    mediaLabels: Map<Long, List<String>> = emptyMap(),
    onFilterSelected: (MediaFilterChip) -> Unit,
    onMediaClick: (MediaItem, Int) -> Unit,
    onMediaLongClick: (MediaItem) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    // Efeito para rolar até a posição salva ao voltar do viewer
    LaunchedEffect(initialScrollPosition) {
        if (initialScrollPosition >= 0) {
            kotlinx.coroutines.delay(50)
            gridState.scrollToItem(initialScrollPosition)
        }
    }

    val filteredMediaGroups = remember(mediaGroups, searchQuery, mediaLabels) {
        if (searchQuery.isBlank()) {
            mediaGroups
        } else {
            mediaGroups.map { group ->
                group.copy(items = group.items.filter { item ->
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    mediaLabels[item.id]?.any { it.contains(searchQuery, ignoreCase = true) } == true
                })
            }.filter { it.items.isNotEmpty() }
        }
    }

    val totalFound = remember(filteredMediaGroups) {
        filteredMediaGroups.sumOf { it.items.size }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Surface)
    ) {
        if (isSelectionMode) {
            SelectionHeader(
                count = selectedItems.size,
                onClose = onClearSelection,
                onDelete = onDeleteSelected
            )
        } else {
            Header(
                onSettingsClick = onSettingsClick,
                onSearchClick = onSearchClick
            )
        }

        if (isSearching) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Primary.copy(alpha = 0.05f))
                    .padding(16.dp)
            ) {
                Text(
                    text = if (totalFound > 0) "$totalFound mídias encontradas" else "Nenhuma mídia encontrada",
                    color = Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                
                if (searchQuery.isBlank() && searchSuggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        text = "Sugestões de busca:",
                        color = OnSurface.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        searchSuggestions.forEach { suggestion ->
                            SuggestionChip(
                                onClick = { searchQuery = suggestion },
                                label = { Text(suggestion, fontSize = 12.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    labelColor = Primary
                                )
                            )
                        }
                    }
                }
            }
        }

        FilterChips(
            selectedFilter = selectedFilter,
            onFilterSelected = onFilterSelected,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        if (isLoading && mediaGroups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Primary)
            }
        } else if (filteredMediaGroups.isEmpty() && !isLoading) {
            EmptyState()
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    filteredMediaGroups.forEach { group ->
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
                                onClick = { onMediaClick(item, gridState.firstVisibleItemIndex) },
                                onLongClick = { onMediaLongClick(item) }
                            )
                        }
                    }
                }

                // Navegação Rápida (Estilo Xiaomi)
                if (filteredMediaGroups.isNotEmpty() && !isSelectionMode) {
                    FastScroller(
                        gridState = gridState,
                        mediaGroups = filteredMediaGroups,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(top = 100.dp, bottom = 100.dp)
                    )
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
private fun Header(
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderBg)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Fotos",
                color = OnSurface,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, contentDescription = "Buscar", tint = OnSurface)
                }

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF4648d4), Color(0xFF0058bc))
                            )
                        )
                        .clickable { onSettingsClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "EU",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Nenhuma mídia encontrada",
            color = OnSurface,
            fontSize = 16.sp
        )
    }
}
