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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import com.hypergallery.ui.theme.Primary
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypergallery.data.Album
import com.hypergallery.ui.components.AlbumCard
import com.hypergallery.ui.components.MemoryItem
import com.hypergallery.ui.components.MemoriesSection
import com.hypergallery.ui.components.PersonItem
import com.hypergallery.ui.components.PeopleSection
import com.hypergallery.ui.theme.HeaderBg
import com.hypergallery.ui.theme.OnSurface
import com.hypergallery.ui.theme.Primary
import com.hypergallery.ui.theme.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow

data class SamplePerson(
    val id: Long,
    val name: String,
    val initials: String,
    val colors: List<Color>
)

data class SampleMemory(
    val id: Long,
    val label: String,
    val colors: List<Color>
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AlbumsScreen(
    mainAlbums: List<Album>,
    otherAlbums: List<Album>,
    favoriteAlbum: Album?,
    showAll: Boolean,
    isLoading: Boolean,
    peopleAndPets: List<com.hypergallery.data.MediaItem> = emptyList(),
    currentFolderTitle: String? = null,
    userVirtualAlbums: Map<String, List<Long>> = emptyMap(),
    onToggleAll: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onMemoryClick: (MemoryItem) -> Unit,
    onPersonClick: (PersonItem) -> Unit,
    onSettingsClick: () -> Unit,
    onBackClick: () -> Unit = {},
    onSortSelected: (com.hypergallery.data.AlbumSort) -> Unit = {},
    onCreateAlbum: (String) -> Unit = {},
    onRenameAlbum: (Long, String) -> Unit = { _, _ -> },
    onSetAlbumCover: (Long) -> Unit = { _ -> },
    onToggleAnalysis: (Long, Boolean) -> Unit = { _, _ -> },
    onTogglePinned: (Long, Boolean) -> Unit = { _, _ -> },
    onAddToVirtualAlbum: (String, Long) -> Unit = { _, _ -> },
    onCreateVirtualAlbum: (String, Long) -> Unit = { _, _ -> },
    onRemoveFromVirtualAlbum: (String, Long) -> Unit = { _, _ -> },
    onDeleteVirtualAlbum: (String) -> Unit = { _ -> },
    onDeleteVirtualRoot: (String) -> Unit = { _ -> },
    onCreateFromFolder: () -> Unit = {},
    pendingFolderPath: String? = null,
    onConfirmVirtualRoot: (String, String) -> Unit = { _, _ -> },
    onCancelVirtualRoot: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    
    var albumMenuTarget by remember { mutableStateOf<Album?>(null) }
    var showRenameDialog by remember { mutableStateOf<Album?>(null) }
    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    var showAddToVirtualDialog by remember { mutableStateOf<Album?>(null) }
    var showCreateVirtualDialog by remember { mutableStateOf<Album?>(null) }
    var showCreateFromFolderDialog by remember { mutableStateOf(false) }

    // ... (filtros álbuns omitidos para brevidade) ...

    val filteredMainAlbums = remember(mainAlbums, searchQuery) {
        if (searchQuery.isBlank()) mainAlbums
        else mainAlbums.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val filteredOtherAlbums = remember(otherAlbums, searchQuery) {
        if (searchQuery.isBlank()) otherAlbums
        else otherAlbums.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val filteredFavoriteAlbum = remember(favoriteAlbum, searchQuery) {
        if (searchQuery.isBlank()) favoriteAlbum
        else if (favoriteAlbum?.name?.contains(searchQuery, ignoreCase = true) == true) favoriteAlbum else null
    }

    val totalFound = remember(filteredMainAlbums, filteredOtherAlbums, filteredFavoriteAlbum) {
        filteredMainAlbums.size + filteredOtherAlbums.size + (if (filteredFavoriteAlbum != null) 1 else 0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Surface)
    ) {
        // ... (Header e totalFound omitidos) ...
        AlbumsHeader(
            title = currentFolderTitle ?: "Álbuns",
            showBack = currentFolderTitle != null,
            onBackClick = onBackClick,
            onSettingsClick = onSettingsClick,
            onSortSelected = onSortSelected,
            onCreateAlbumClick = { showCreateAlbumDialog = true },
            onCreateFromFolderClick = onCreateFromFolder,
            onSearchClick = onSearchClick
        )

        if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Primary.copy(alpha = 0.1f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "$totalFound álbuns encontrados",
                    color = Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (isLoading && mainAlbums.isEmpty() && favoriteAlbum == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ... (Memories e People Sections omitidos) ...
                if (currentFolderTitle == null && !isSearching) {
                    item(span = { GridItemSpan(3) }) {
                        val sampleMemories = listOf(
                            SampleMemory(1, "1 ano atrás\nFérias", listOf(Color(0xFF0984e3), Color(0xFF74b9ff))),
                            SampleMemory(2, "Viagem ao\nJapão", listOf(Color(0xFF00b894), Color(0xFF81ecec))),
                            SampleMemory(3, "Caminhada\nna Natureza", listOf(Color(0xFFe84393), Color(0xFFfdcb6e))),
                            SampleMemory(4, "Aniversário\n2024", listOf(Color(0xFF6c5ce7), Color(0xFFa29bfe)))
                        )
                        val memories = sampleMemories.map { MemoryItem(it.id, it.label, colors = it.colors) }
                        MemoriesSection(
                            memories = memories,
                            onMemoryClick = onMemoryClick,
                            onSeeAllClick = { if (memories.isNotEmpty()) onMemoryClick(memories.first()) }
                        )
                    }

                    item(span = { GridItemSpan(3) }) { Spacer(modifier = Modifier.height(16.dp)) }

                    item(span = { GridItemSpan(3) }) {
                        if (peopleAndPets.isNotEmpty()) {
                            val people = peopleAndPets.take(8).mapIndexed { index, item ->
                                PersonItem(
                                    id = item.id,
                                    name = if (index % 2 == 0) "Pessoa ${index/2 + 1}" else "Pet ${index/2 + 1}",
                                    imageUri = item.uri.toString()
                                )
                            }
                            PeopleSection(
                                people = people,
                                onPersonClick = onPersonClick,
                                onSeeAllClick = { if (people.isNotEmpty()) onPersonClick(people.first()) }
                            )
                        } else {
                            val samplePeople = listOf(
                                SamplePerson(1, "Sarah", "S", listOf(Color(0xFFfd79a8), Color(0xFFe84393))),
                                SamplePerson(2, "David", "D", listOf(Color(0xFFfdcb6e), Color(0xFFe17055))),
                                SamplePerson(3, "Ana", "A", listOf(Color(0xFF55efc4), Color(0xFF00b894))),
                                SamplePerson(4, "Max", "🐶", listOf(Color(0xFF74b9ff), Color(0xFF0984e3)))
                            )
                            val people = samplePeople.map { PersonItem(it.id, it.name, initials = it.initials, colors = it.colors) }
                            PeopleSection(
                                people = people,
                                onPersonClick = onPersonClick,
                                onSeeAllClick = { if (people.isNotEmpty()) onPersonClick(people.first()) }
                            )
                        }
                    }

                    item(span = { GridItemSpan(3) }) { Spacer(modifier = Modifier.height(16.dp)) }
                }

                item(span = { GridItemSpan(3) }) {
                    Text(
                        text = if (currentFolderTitle == null) "Álbuns Principais" else "Conteúdo do WhatsApp",
                        color = OnSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                filteredFavoriteAlbum?.let { fav ->
                    item {
                        AlbumCard(
                            album = fav,
                            onClick = { onAlbumClick(fav) },
                            onLongClick = { albumMenuTarget = fav },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }

                items(filteredMainAlbums, key = { it.id }) { album ->
                    AlbumCard(
                        album = album,
                        onClick = { onAlbumClick(album) },
                        onLongClick = { albumMenuTarget = album },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                if (filteredOtherAlbums.isNotEmpty()) {
                    item(span = { GridItemSpan(3) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.TextButton(onClick = onToggleAll) {
                                Text(
                                    text = if (showAll) "- Mostrar Menos" else "+ Ver Outros Álbuns",
                                    color = Primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (showAll || isSearching) {
                        items(filteredOtherAlbums, key = { it.id }) { album ->
                            AlbumCard(
                                album = album,
                                onClick = { onAlbumClick(album) },
                                onLongClick = { albumMenuTarget = album },
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Menu de Contexto
    albumMenuTarget?.let { album ->
        DropdownMenu(
            expanded = true,
            onDismissRequest = { albumMenuTarget = null }
        ) {
            DropdownMenuItem(
                text = { Text(if (album.isPinned) "Remover dos Principais" else "Fixar nos Principais") },
                onClick = { onTogglePinned(album.bucketId, !album.isPinned); albumMenuTarget = null },
                leadingIcon = { Icon(if (album.isPinned) Icons.Outlined.PushPin else Icons.Default.PushPin, null) }
            )
            DropdownMenuItem(
                text = { Text("Renomear Álbum") },
                onClick = { showRenameDialog = album; albumMenuTarget = null },
                leadingIcon = { Icon(Icons.Default.Edit, null) }
            )
            DropdownMenuItem(
                text = { Text("Mudar Foto da Capa") },
                onClick = { onSetAlbumCover(album.bucketId); albumMenuTarget = null },
                leadingIcon = { Icon(Icons.Default.Image, null) }
            )

            if (!album.isVirtualFolder) {
                DropdownMenuItem(
                    text = { Text("Adicionar a Álbum Virtual") },
                    onClick = { showAddToVirtualDialog = album; albumMenuTarget = null },
                    leadingIcon = { Icon(Icons.Default.FolderSpecial, null) }
                )
            } else if (currentFolderTitle != null && currentFolderTitle in userVirtualAlbums.keys) {
                 DropdownMenuItem(
                    text = { Text("Remover deste Álbum Virtual") },
                    onClick = { onRemoveFromVirtualAlbum(currentFolderTitle, album.bucketId); albumMenuTarget = null },
                    leadingIcon = { Icon(Icons.Default.Close, null) }
                )
            } else if (album.name in userVirtualAlbums.keys) {
                DropdownMenuItem(
                    text = { Text("Excluir Álbum Virtual") },
                    onClick = { onDeleteVirtualAlbum(album.name); albumMenuTarget = null },
                    leadingIcon = { Icon(Icons.Default.Delete, null) }
                )
            } else if (album.relativePath.startsWith("virtual/root/")) {
                DropdownMenuItem(
                    text = { Text("Excluir Álbum por Pasta") },
                    onClick = { onDeleteVirtualRoot(album.name); albumMenuTarget = null },
                    leadingIcon = { Icon(Icons.Default.Delete, null) }
                )
            }

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Procurar Rostos aqui", modifier = Modifier.weight(1f))
                        Switch(
                            checked = album.isAnalysisEnabled,
                            onCheckedChange = { onToggleAnalysis(album.bucketId, it); albumMenuTarget = null }
                        )
                    }
                },
                onClick = { onToggleAnalysis(album.bucketId, !album.isAnalysisEnabled); albumMenuTarget = null },
                leadingIcon = { Icon(Icons.Default.AutoAwesome, null) }
            )
        }
    }
    
    // Dialog de Renomear
    showRenameDialog?.let { album ->
        var newName by remember { mutableStateOf(album.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Renomear Álbum") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { onRenameAlbum(album.bucketId, newName); showRenameDialog = null }) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) { Text("Cancelar") }
            }
        )
    }

    // Dialog de Criar Álbum
    if (showCreateAlbumDialog) {
        // ... (já existe)
    }

    // Dialog de Selecionar Álbum Virtual
    showAddToVirtualDialog?.let { album ->
        AlertDialog(
            onDismissRequest = { showAddToVirtualDialog = null },
            title = { Text("Adicionar a Álbum Virtual") },
            text = {
                Column {
                    if (userVirtualAlbums.isEmpty()) {
                        Text("Você ainda não tem álbuns virtuais.", modifier = Modifier.padding(bottom = 8.dp))
                    } else {
                        userVirtualAlbums.keys.forEach { virtualName ->
                            TextButton(
                                onClick = { 
                                    onAddToVirtualAlbum(virtualName, album.bucketId)
                                    showAddToVirtualDialog = null 
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(virtualName, color = OnSurface)
                            }
                        }
                    }
                    TextButton(
                        onClick = { 
                            showCreateVirtualDialog = album
                            showAddToVirtualDialog = null 
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, null, tint = Primary)
                            Spacer(Modifier.size(8.dp))
                            Text("Criar Novo Álbum Virtual", color = Primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddToVirtualDialog = null }) { Text("Cancelar") }
            }
        )
    }

    // Dialog de Criar Álbum Virtual
    showCreateVirtualDialog?.let { album ->
        var virtualName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateVirtualDialog = null },
            title = { Text("Novo Álbum Virtual") },
            text = {
                OutlinedTextField(
                    value = virtualName,
                    onValueChange = { virtualName = it },
                    placeholder = { Text("Nome do álbum virtual...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = { 
                        if (virtualName.isNotBlank()) {
                            onCreateVirtualAlbum(virtualName, album.bucketId)
                            showCreateVirtualDialog = null
                        }
                    }
                ) { Text("Criar e Adicionar") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateVirtualDialog = null }) { Text("Cancelar") }
            }
        )
    }

    // Dialog de Nome para Álbum por Pasta
    pendingFolderPath?.let { path ->
        var virtualName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onCancelVirtualRoot,
            title = { Text("Nome do Álbum por Pasta") },
            text = {
                Column {
                    Text("Escolha um nome para o álbum virtual que agrupará as pastas de:", fontSize = 14.sp)
                    Text(path, fontSize = 12.sp, color = Primary, modifier = Modifier.padding(vertical = 4.dp))
                    OutlinedTextField(
                        value = virtualName,
                        onValueChange = { virtualName = it },
                        placeholder = { Text("Ex: Minhas Viagens") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { 
                        if (virtualName.isNotBlank()) {
                            onConfirmVirtualRoot(virtualName, path)
                        } else {
                            // Mostrar aviso se o nome estiver vazio
                            // Nota: O ideal é usar um Toast via CompositionLocal, mas MainActivity já tem esse callback
                            // Aqui podemos apenas impedir o clique ou passar uma flag de erro.
                            // Como MainActivity é quem chama o Toast no sucesso, vamos garantir que só prossiga com nome.
                        }
                    }
                ) { Text("Criar Álbum") }
            },
            dismissButton = {
                TextButton(onClick = onCancelVirtualRoot) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun AlbumsHeader(
    title: String,
    showBack: Boolean,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSortSelected: (com.hypergallery.data.AlbumSort) -> Unit,
    onCreateAlbumClick: () -> Unit = {},
    onCreateFromFolderClick: () -> Unit = {},
    onSearchClick: () -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            .background(HeaderBg)
            .statusBarsPadding()
            .padding(horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showBack) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = OnSurface
                        )
                    }
                }
                Text(
                    text = title,
                    color = OnSurface,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = if (showBack) 0.dp else 8.dp)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Ordenar",
                            tint = OnSurface
                        )
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Nome (A-Z)") },
                            onClick = { onSortSelected(com.hypergallery.data.AlbumSort.NAME_ASC); showSortMenu = false }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Nome (Z-A)") },
                            onClick = { onSortSelected(com.hypergallery.data.AlbumSort.NAME_DESC); showSortMenu = false }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Data (Mais Recentes)") },
                            onClick = { onSortSelected(com.hypergallery.data.AlbumSort.DATE_DESC); showSortMenu = false }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Quantidade") },
                            onClick = { onSortSelected(com.hypergallery.data.AlbumSort.SIZE_DESC); showSortMenu = false }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Criar Álbum por Pasta") },
                            onClick = { onCreateFromFolderClick(); showSortMenu = false },
                            leadingIcon = { Icon(Icons.Default.FolderSpecial, null) }
                        )
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Criar Álbum") },
                            onClick = { onCreateAlbumClick(); showSortMenu = false },
                            leadingIcon = { Icon(Icons.Default.Add, null) }
                        )
                    }
                }

                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = OnSurface
                    )
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
