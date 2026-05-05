package com.hypergallery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hypergallery.ui.theme.ChipInactiveText
import com.hypergallery.ui.theme.OnSurface
import com.hypergallery.ui.theme.OnSurfaceVariant
import com.hypergallery.ui.theme.OutlineVariant
import com.hypergallery.ui.theme.SurfaceContainer
import com.hypergallery.ui.theme.SurfaceContainerHigh

data class MemoryItem(
    val id: Long,
    val label: String,
    val imageUri: String? = null,
    val colors: List<Color> = listOf(Color(0xFF0984e3), Color(0xFF74b9ff))
)

data class PersonItem(
    val id: Long,
    val name: String,
    val imageUri: String? = null,
    val initials: String = "",
    val colors: List<Color>? = null
)

@Composable
fun MemoriesSection(
    memories: List<MemoryItem>,
    onMemoryClick: (MemoryItem) -> Unit,
    onSeeAllClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SectionHeader(
            title = "Memórias",
            action = "Ver tudo",
            onActionClick = onSeeAllClick
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(memories) { memory ->
                MemoryCard(
                    memory = memory,
                    onClick = { onMemoryClick(memory) }
                )
            }
        }
    }
}

@Composable
fun MemoryCard(
    memory: MemoryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 120.dp, height = 160.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (memory.imageUri == null) {
                    Modifier.background(Brush.linearGradient(memory.colors))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
    ) {
        if (memory.imageUri != null) {
            AsyncImage(
                model = memory.imageUri,
                contentDescription = memory.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xA6000000)),
                        startY = 100f
                    )
                )
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            Text(
                text = memory.label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PeopleSection(
    people: List<PersonItem>,
    onPersonClick: (PersonItem) -> Unit,
    onSeeAllClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SectionHeader(
            title = "Pessoas & Pets",
            action = "Ver tudo",
            onActionClick = onSeeAllClick
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(people) { person ->
                PersonItem(
                    person = person,
                    onClick = { onPersonClick(person) }
                )
            }
        }
    }
}

@Composable
fun PersonItem(
    person: PersonItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .then(
                    if (person.colors != null) {
                        Modifier.background(Brush.linearGradient(person.colors))
                    } else {
                        Modifier.background(SurfaceContainerHigh)
                    }
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (person.imageUri != null) {
                AsyncImage(
                    model = person.imageUri,
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (person.initials.isNotEmpty()) {
                Text(
                    text = person.initials,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Adicionar",
                    tint = ChipInactiveText,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Text(
            text = person.name,
            color = OnSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    action: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = OnSurface,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterStart)
        )

        if (action != null) {
            Text(
                text = action,
                color = com.hypergallery.ui.theme.Primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable { onActionClick?.invoke() }
            )
        }
    }
}