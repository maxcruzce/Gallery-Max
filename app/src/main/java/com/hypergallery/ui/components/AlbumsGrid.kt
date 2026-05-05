package com.hypergallery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hypergallery.data.Album
import com.hypergallery.data.AlbumType
import com.hypergallery.ui.theme.CountText
import com.hypergallery.ui.theme.FavHeart
import com.hypergallery.ui.theme.OnSurface
import com.hypergallery.ui.theme.Primary
import com.hypergallery.ui.theme.SurfaceContainer

@Composable
fun AlbumsGrid(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    onLongClick: (Album) -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(albums, key = { it.id }) { album ->
            AlbumCard(
                album = album,
                onClick = { onAlbumClick(album) },
                onLongClick = { onLongClick(album) }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceContainer)
        ) {
            if (album.type == AlbumType.FAVORITES) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFfff0f3)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = FavHeart,
                        modifier = Modifier.size(40.dp)
                    )
                }
            } else if (album.isVirtualFolder && album.mosaicUris.size >= 2) {
                AlbumMosaic(uris = album.mosaicUris, modifier = Modifier.fillMaxSize())
            } else {
                AsyncImage(
                    model = album.coverUri,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            if (album.isAnalysisEnabled) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Primary.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Text(
            text = album.name,
            color = OnSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp)
        )

        Text(
            text = formatAlbumCount(album.itemCount),
            color = CountText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 2.dp)
        )
    }
}

private fun formatAlbumCount(count: Int): String {
    return when {
        count >= 1000 -> String.format("%.1fk itens", count / 1000.0)
        count == 1 -> "1 item"
        else -> "$count itens"
    }
}

@Composable
fun AlbumMosaic(uris: List<android.net.Uri>, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        when (uris.size) {
            2 -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = uris[0],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    Box(modifier = Modifier.size(1.dp).fillMaxHeight().background(Color.White))
                    AsyncImage(
                        model = uris[1],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
            3 -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = uris[0],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    Box(modifier = Modifier.size(1.dp).fillMaxHeight().background(Color.White))
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        AsyncImage(
                            model = uris[1],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White))
                        AsyncImage(
                            model = uris[2],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    }
                }
            }
            else -> { // 4 ou mais
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        AsyncImage(
                            model = uris[0],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        Box(modifier = Modifier.size(1.dp).fillMaxHeight().background(Color.White))
                        AsyncImage(
                            model = uris[1],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White))
                    Row(modifier = Modifier.weight(1f)) {
                        AsyncImage(
                            model = uris[2],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        Box(modifier = Modifier.size(1.dp).fillMaxHeight().background(Color.White))
                        AsyncImage(
                            model = uris[3],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}
