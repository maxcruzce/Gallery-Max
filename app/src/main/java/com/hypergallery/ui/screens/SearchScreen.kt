package com.hypergallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypergallery.data.MediaGroup
import com.hypergallery.data.MediaItem
import com.hypergallery.data.Album
import com.hypergallery.ui.components.PhotoCell
import com.hypergallery.ui.components.AlbumCard
import com.hypergallery.ui.theme.OnSurface
import com.hypergallery.ui.theme.Primary
import com.hypergallery.ui.theme.Surface
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    searchResults: List<MediaGroup>,
    albumResults: List<Album> = emptyList(),
    onQueryChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onAlbumClick: (Album) -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val gridState = rememberLazyGridState()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(modifier = Modifier.fillMaxSize().background(Surface)) {
        // Search Header
        TopAppBar(
            title = {
                TextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onQueryChange(it)
                    },
                    placeholder = { Text("Buscar fotos, textos, locais...") },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = ""; onQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpar")
                            }
                        }
                    }
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                }
            }
        )

        if (query.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = OnSurface.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Pesquise por nomes, datas ou textos nas imagens",
                        color = OnSurface.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            }
        } else if (searchResults.isEmpty() && albumResults.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Nenhum resultado encontrado para \"$query\"", color = OnSurface.copy(alpha = 0.5f))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Seção de Álbuns
                if (albumResults.isNotEmpty()) {
                    item(span = { GridItemSpan(3) }) {
                        Column(modifier = Modifier.padding(vertical = 12.dp)) {
                            Text(
                                text = "Álbuns",
                                color = OnSurface,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(albumResults, key = { it.id }) { album ->
                                    Box(modifier = Modifier.width(110.dp)) {
                                        AlbumCard(
                                            album = album,
                                            onClick = { onAlbumClick(album) }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            if (searchResults.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = OnSurface.copy(alpha = 0.1f))
                            }
                        }
                    }
                }

                searchResults.forEach { group ->
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
                            isSelected = false,
                            isSelectionMode = false,
                            onClick = { onMediaClick(item) },
                            onLongClick = { }
                        )
                    }
                }
            }
        }
    }
}
