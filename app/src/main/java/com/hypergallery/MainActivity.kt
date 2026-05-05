package com.hypergallery

import android.Manifest
import android.os.Build
import android.content.Intent
import android.util.Log
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.hypergallery.ui.components.GalleryTab
import com.hypergallery.ui.components.NavigationPill
import com.hypergallery.ui.screens.AlbumDetailScreen
import com.hypergallery.ui.screens.AlbumsScreen
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.zIndex
import com.hypergallery.ui.screens.MediaViewerScreen
import com.hypergallery.ui.screens.MomentsScreen
import com.hypergallery.ui.screens.PeopleScreen
import com.hypergallery.ui.screens.PhotosScreen
import com.hypergallery.ui.screens.SearchScreen
import com.hypergallery.ui.screens.SettingsScreen
import com.hypergallery.ui.theme.HyperGalleryTheme
import com.hypergallery.viewmodel.GalleryUiState
import com.hypergallery.viewmodel.GalleryViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: GalleryViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsState()

            HyperGalleryTheme(darkTheme = uiState.isDarkMode) {
                GalleryApp(viewModel, uiState)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryApp(viewModel: GalleryViewModel, uiState: GalleryUiState) {
    val localContext = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = localContext as? androidx.activity.ComponentActivity

    val documentScannerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val scanResult = com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanResult?.pages?.getOrNull(0)?.let { page ->
                // O scanner gera uma nova imagem ajustada. 
                // No futuro, podemos salvar isso na galeria ou apenas visualizar.
                android.widget.Toast.makeText(localContext, "Documento ajustado com sucesso!", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val backupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.importWhatsAppBackup(it) }
    }

    val folderPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri: android.net.Uri? ->
        uri?.let { treeUri ->
            try {
                val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                val split = docId.split(":")
                val type = split[0]
                val relativePath = if (split.size > 1) split[1] else ""
                
                val fullPath = when {
                    "primary".equals(type, ignoreCase = true) -> "/storage/emulated/0/$relativePath"
                    else -> "/storage/$type/$relativePath"
                }.removeSuffix("/")
                
                viewModel.setPendingFolder(fullPath)
                android.widget.Toast.makeText(localContext, "Pasta: $fullPath", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Erro ao acessar pasta", e)
                android.widget.Toast.makeText(localContext, "Erro ao acessar pasta", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val permissionState = rememberMultiplePermissionsState(permissions)

    LaunchedEffect(Unit) {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = android.net.Uri.parse("package:${localContext.packageName}")
                }
                localContext.startActivity(intent)
            }
        }
    }

    val pagerState = rememberPagerState(initialPage = 0) { 2 }

    // Sincroniza o Pager com o currentTab do ViewModel (Importante para o "Ir para Álbum")
    LaunchedEffect(uiState.currentTab) {
        val targetPage = if (uiState.currentTab == GalleryTab.PHOTOS) 0 else 1
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val tab = if (page == 0) GalleryTab.PHOTOS else GalleryTab.ALBUMS
            if (uiState.currentTab != tab) {
                viewModel.setCurrentTab(tab)
            }
        }
    }

    BackHandler(enabled = uiState.isViewerOpen || uiState.isDetailsOpen || uiState.selectedAlbum != null || uiState.isSelectionMode || uiState.isSettingsOpen || uiState.currentFolderPath != null || uiState.isPeopleOpen || uiState.isMomentsOpen || uiState.selectedPerson != null || uiState.isSearchOpen) {
        when {
            uiState.isDetailsOpen -> viewModel.closeDetails()
            uiState.isViewerOpen -> viewModel.closeViewer()
            uiState.isSettingsOpen -> viewModel.closeSettings()
            uiState.isSearchOpen -> viewModel.closeSearch()
            uiState.selectedPerson != null -> viewModel.clearSelectedPerson()
            uiState.selectedAlbum != null -> {
                if (uiState.currentFolderPath != null) {
                    viewModel.clearSelectedAlbum()
                } else {
                    viewModel.clearSelectedAlbum()
                }
            }
            uiState.isPeopleOpen -> viewModel.closePeople()
            uiState.isMomentsOpen -> viewModel.closeMoments()
            uiState.isSelectionMode -> viewModel.clearSelection()
            uiState.currentFolderPath != null -> viewModel.clearSelectedAlbum()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(if (uiState.isDarkMode) Color.Black else Color(0xFFf2f2f7))) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondBoundsPageCount = 1,
            userScrollEnabled = !uiState.isViewerOpen && !uiState.isDetailsOpen && uiState.selectedAlbum == null && !uiState.isSettingsOpen
        ) { page ->
            if (page == 0) {
                PhotosScreen(
                    mediaGroups = uiState.mediaGroups,
                    isLoading = uiState.isLoading,
                    selectedFilter = uiState.selectedFilter,
                    selectedItems = uiState.selectedItems,
                    isSelectionMode = uiState.isSelectionMode,
                    initialScrollPosition = uiState.albumScrollPosition,
                    searchSuggestions = uiState.searchSuggestions,
                    mediaLabels = uiState.mediaLabels,
                    onFilterSelected = { viewModel.setFilter(it) },
                    onMediaClick = { item, scrollPos ->
                        if (uiState.isSelectionMode) viewModel.toggleSelection(item.id)
                        else {
                            val allMedia = uiState.mediaGroups.flatMap { it.items }
                            viewModel.openViewerWithScroll(allMedia, allMedia.indexOf(item), scrollPos)
                        }
                    },
                    onMediaLongClick = { viewModel.toggleSelection(it.id) },
                    onClearSelection = { viewModel.clearSelection() },
                    onDeleteSelected = { viewModel.deleteSelectedMedia() },
                    onSettingsClick = { viewModel.openSettings() },
                    onSearchClick = { viewModel.openSearch() }
                )
            } else {
                AlbumsScreen(
                    mainAlbums = uiState.mainAlbums,
                    otherAlbums = uiState.otherAlbums,
                    favoriteAlbum = uiState.favoriteAlbum,
                    showAll = uiState.showAllAlbums,
                    isLoading = uiState.isLoading,
                    peopleAndPets = uiState.peopleAndPets,
                    currentFolderTitle = uiState.currentFolderTitle,
                    userVirtualAlbums = uiState.userVirtualAlbums,
                    onToggleAll = { viewModel.toggleShowAllAlbums() },
                    onAlbumClick = { viewModel.selectAlbum(it) },
                    onMemoryClick = { viewModel.openMoments() },
                    onPersonClick = { viewModel.openPeople() },
                    onSettingsClick = { viewModel.openSettings() },
                    onBackClick = { viewModel.clearSelectedAlbum() },
                    onSortSelected = { viewModel.setAlbumSort(it) },
                    onCreateAlbum = { viewModel.createAlbum(it) },
                    onRenameAlbum = { id, name -> viewModel.renameAlbum(id, name) },
                    onSetAlbumCover = { viewModel.startSelectingCover(it) },
                    onToggleAnalysis = { id, en -> viewModel.toggleAlbumAnalysis(id, en) },
                    onTogglePinned = { id, pi -> viewModel.toggleAlbumPinned(id, pi) },
                    onAddToVirtualAlbum = { name, id -> viewModel.addAlbumToVirtual(name, id) },
                    onCreateVirtualAlbum = { name, id -> viewModel.createVirtualAlbum(name, id) },
                    onRemoveFromVirtualAlbum = { name, id -> viewModel.removeAlbumFromVirtual(name, id) },
                    onDeleteVirtualAlbum = { viewModel.deleteVirtualAlbum(it) },
                    onDeleteVirtualRoot = { viewModel.deleteVirtualFolderRoot(it) },
                    onCreateFromFolder = { folderPickerLauncher.launch(null) },
                    onSearchClick = { viewModel.openSearch() },
                    pendingFolderPath = uiState.pendingFolderPath,
                    onConfirmVirtualRoot = { name, path -> 
                        if (name.isNotBlank()) {
                            viewModel.createVirtualAlbumFromFolder(name, path)
                            viewModel.clearPendingFolder()
                            android.widget.Toast.makeText(localContext, "Álbum '$name' criado!", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(localContext, "Por favor, digite um nome para o álbum", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCancelVirtualRoot = { viewModel.clearPendingFolder() }
                )
            }
        }

        // Camada 7: Busca
        if (uiState.isSearchOpen) {
            SearchScreen(
                searchResults = uiState.searchResults,
                onQueryChange = { viewModel.onSearchQueryChanged(it) },
                onBackClick = { viewModel.closeSearch() },
                onMediaClick = { item ->
                    viewModel.openViewer(listOf(item), 0)
                }
            )
        }

        if (uiState.selectedAlbum != null) {
            AlbumDetailScreen(
                album = uiState.selectedAlbum!!,
                mediaGroups = uiState.albumMedia,
                isLoading = uiState.isAlbumLoading,
                selectedItems = uiState.selectedItems,
                isSelectionMode = uiState.isSelectionMode,
                initialScrollPosition = uiState.albumScrollPosition,
                onBackClick = { viewModel.clearSelectedAlbum() },
                onMediaClick = { item, scrollPos ->
                    if (uiState.isSelectionMode) viewModel.toggleSelection(item.id)
                    else {
                        val allMedia = uiState.albumMedia.flatMap { it.items }
                        viewModel.openViewerWithScroll(allMedia, allMedia.indexOf(item), scrollPos)
                    }
                },
                onMediaLongClick = { viewModel.toggleSelection(it.id) },
                onClearSelection = { viewModel.clearSelection() },
                onDeleteSelected = { viewModel.deleteSelectedMedia() },
                onSortSelected = { viewModel.setAlbumSort(it) },
                selectingCoverForAlbumId = uiState.selectingCoverForAlbumId,
                onSelectCover = { viewModel.setAlbumCover(uiState.selectingCoverForAlbumId!!, it) },
                onCancelSelectCover = { viewModel.cancelSelectingCover() }
            )
        }

        // Camada 4: Configurações HyperOS Slide
        AnimatedVisibility(
            visible = uiState.isSettingsOpen,
            enter = slideInHorizontally(spring(Spring.DampingRatioLowBouncy)) { it } + fadeIn(),
            exit = slideOutHorizontally(spring(Spring.StiffnessLow)) { it } + fadeOut(),
            modifier = Modifier.zIndex(15f)
        ) {
            SettingsScreen(
                isDarkMode = uiState.isDarkMode,
                isWhatsAppParserEnabled = uiState.isWhatsAppParserEnabled,
                whatsappBackupName = uiState.whatsappBackupName,
                importProgress = uiState.importProgress,
                saveExifData = uiState.saveExifData,
                isAnalyzing = uiState.isAnalyzing,
                analysisProgress = uiState.analysisProgress,
                analyzedCount = uiState.analyzedCount,
                totalToAnalyze = uiState.totalToAnalyze,
                isOptimizing = uiState.isOptimizing,
                optimizationStatus = uiState.optimizationStatus,
                onToggleDarkMode = { viewModel.toggleDarkMode() },
                onToggleWhatsAppParser = { viewModel.toggleWhatsAppParser() },
                onImportBackupClick = { backupLauncher.launch(arrayOf("text/plain")) },
                onToggleSaveExif = { viewModel.toggleSaveExif() },
                onBackClick = { viewModel.closeSettings() },
                onOptimizePeople = { viewModel.optimizePeople() }
            )
        }

        // Camada 5: Pessoas
        AnimatedVisibility(
            visible = uiState.isPeopleOpen,
            enter = slideInHorizontally(spring(Spring.DampingRatioLowBouncy)) { it } + fadeIn(),
            exit = slideOutHorizontally(spring(Spring.StiffnessLow)) { it } + fadeOut(),
            modifier = Modifier.zIndex(5f)
        ) {
            PeopleScreen(
                peopleWithFaces = uiState.peopleWithFaces,
                isAnalyzing = uiState.isAnalyzing,
                analysisProgress = uiState.analysisProgress,
                analyzedCount = uiState.analyzedCount,
                totalToAnalyze = uiState.totalToAnalyze,
                faceAnalysisStatus = uiState.faceAnalysisStatus,
                faceModelAvailable = uiState.faceModelAvailable,
                onBackClick = { viewModel.closePeople() },
                onRenamePerson = { id, name -> viewModel.renamePerson(id, name) },
                onMergePeople = { t, s -> viewModel.mergePeople(t, s) },
                onRemoveFace = { p, f -> viewModel.removeFace(p, f) },
                onDeletePerson = { viewModel.deletePerson(it) },
                onSearchFacesForPerson = { viewModel.searchFacesForPerson(it) },
                onPersonClick = { viewModel.selectPerson(it) },
                onOptimizePeople = { viewModel.optimizePeople() }
            )
        }

        if (uiState.selectedPerson != null) {
            val person = uiState.selectedPerson!!
            AlbumDetailScreen(
                album = com.hypergallery.data.Album(person.id.hashCode().toLong(), person.name ?: "Pessoa", android.net.Uri.EMPTY, uiState.personMedia.sumOf { it.items.size }, -1),
                mediaGroups = uiState.personMedia,
                isLoading = uiState.isLoading,
                selectedItems = uiState.selectedItems,
                isSelectionMode = uiState.isSelectionMode,
                onBackClick = { viewModel.clearSelectedPerson() },
                onMediaClick = { item, _ ->
                    if (uiState.isSelectionMode) viewModel.toggleSelection(item.id)
                    else {
                        val allMedia = uiState.personMedia.flatMap { it.items }
                        viewModel.openViewer(allMedia, allMedia.indexOf(item), fromPerson = true)
                    }
                },
                onMediaLongClick = { viewModel.toggleSelection(it.id) },
                onClearSelection = { viewModel.clearSelection() },
                onDeleteSelected = { viewModel.deleteSelectedMedia() },
                onSortSelected = { viewModel.setAlbumSort(it) },
                selectingCoverForAlbumId = uiState.selectingCoverForAlbumId,
                onSelectCover = { viewModel.setAlbumCover(uiState.selectingCoverForAlbumId!!, it) },
                onCancelSelectCover = { viewModel.cancelSelectingCover() }
            )
        }

        // Camada 7: Busca com transição HyperOS
        AnimatedVisibility(
            visible = uiState.isSearchOpen,
            enter = slideInVertically(spring(Spring.DampingRatioLowBouncy)) { it } + fadeIn(),
            exit = slideOutVertically(spring(Spring.StiffnessLow)) { it } + fadeOut(),
            modifier = Modifier.zIndex(7f)
        ) {
            SearchScreen(
                searchResults = uiState.searchResults,
                onQueryChange = { viewModel.onSearchQueryChanged(it) },
                onBackClick = { viewModel.closeSearch() },
                onMediaClick = { item ->
                    viewModel.openViewer(listOf(item), 0)
                }
            )
        }

        if (uiState.isMomentsOpen) {
            MomentsScreen(moments = uiState.peopleAndPets, onBackClick = { viewModel.closeMoments() }, onMediaClick = { viewModel.openViewer(listOf(it), 0) })
        }

        if (uiState.isViewerOpen) {
            MediaViewerScreen(
                mediaList = uiState.viewerMedia,
                startIndex = uiState.viewerStartIndex,
                onBackClick = { viewModel.closeViewer(it) },
                onDeleteClick = { viewModel.deleteSingleMedia(it) },
                onFavoriteClick = { viewModel.toggleFavorite(it) },
                onInfoClick = { },
                onDetailsClick = { viewModel.openDetails() },
                onGetMetadata = { viewModel.getMetadata(it) },
                onGetFaces = { viewModel.getFacesForMedia(it) },
                onSaveMetadata = { id, d, t -> viewModel.saveMetadata(id, d, t) },
                onGetExifData = { viewModel.getExifData(it) },
                onRenameMedia = { it, n -> viewModel.renameMedia(it, n) },
                onExtractText = { it, res -> viewModel.extractTextFromMedia(it, res) },
                onEditMedia = { mediaItem ->
                    // ... (lógica de edição já implementada)
                    try {
                        val editIntent = Intent(Intent.ACTION_EDIT).apply {
                            setDataAndType(mediaItem.uri, mediaItem.mimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            setPackage("com.miui.mediaeditor")
                        }
                        
                        if (editIntent.resolveActivity(localContext.packageManager) != null) {
                            localContext.startActivity(editIntent)
                        } else {
                            val fallbackIntent = Intent(Intent.ACTION_EDIT).apply {
                                setDataAndType(mediaItem.uri, mediaItem.mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            }
                            localContext.startActivity(Intent.createChooser(fallbackIntent, "Editar com..."))
                        }
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(localContext, "Erro ao abrir editor: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onAdjustDocument = { mediaItem ->
                    try {
                        // Lista de tentativas EXCLUSIVA para o app Bokeh/ExtraPhoto da Xiaomi (Focada em EDITAR imagem existente)
                        val intentCandidates = listOf(
                            // 1. Ação específica de edição de documento da Galeria Xiaomi (frequentemente delegada ao Bokeh)
                            Intent("com.miui.gallery.action.EDIT_DOCUMENT").apply {
                                setPackage("com.miui.gallery")
                            },
                            // 2. Chamada direta para a atividade de Documentos do ExtraPhoto
                            Intent(Intent.ACTION_EDIT).apply {
                                setComponent(android.content.ComponentName(
                                    "com.miui.extraphoto", 
                                    "com.miui.extraphoto.photoflow.DocumentActivity"
                                ))
                            },
                            // 3. Ação genérica de Extra Photo direcionada ao Bokeh
                            Intent("com.miui.extraphoto.action.EXTRA_PHOTO").apply {
                                setPackage("com.miui.extraphoto")
                                putExtra("extra_photo_type", "document")
                                putExtra("is_document_mode", true)
                            },
                            // 4. Edição via MediaEditor (Xiaomi moderno) com flag de documento
                            Intent(Intent.ACTION_EDIT).apply {
                                setPackage("com.miui.mediaeditor")
                                putExtra("miui_editor_type", "document")
                                putExtra("is_document_mode", true)
                                putExtra("allow_crop", true)
                                putExtra("is_from_gallery", true)
                            }
                        )

                        var started = false
                        for (intent in intentCandidates) {
                            intent.setDataAndType(mediaItem.uri, mediaItem.mimeType)
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            
                            // Verifica especificamente se o app alvo responde
                            val resolveInfo = localContext.packageManager.queryIntentActivities(intent, 0)
                            if (resolveInfo.isNotEmpty()) {
                                localContext.startActivity(intent)
                                started = true
                                Log.d("MainActivity", "Iniciado Ajuste Bokeh via: ${intent.component?.shortClassName ?: intent.action}")
                                break
                            }
                        }

                        if (!started) {
                            android.widget.Toast.makeText(localContext, "Não foi possível encontrar a ferramenta de ajuste da Xiaomi no seu dispositivo.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(localContext, "Erro ao abrir ajuste: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onStartManualFaceSelection = { viewModel.startManualFaceSelection() },
                onCancelManualFaceSelection = { viewModel.cancelManualFaceSelection() },
                onUpdateFaceBounds = { id, r -> viewModel.updateFaceBounds(id, r) },
                onAlbumClick = { mediaItem -> viewModel.goToMediaInAlbum(mediaItem) },
                onPersonClick = { personId ->
                    val person = uiState.peopleWithFaces.find { it.first.id == personId }?.first
                    person?.let { viewModel.selectPerson(it) }
                },
                isManualFaceSelectionMode = uiState.isManualFaceSelectionMode,
                isViewingFromPerson = uiState.isViewingFromPerson,
                faces = uiState.currentMediaFaces
            )
        }

        if (!uiState.isViewerOpen && !uiState.isDetailsOpen && !uiState.isSettingsOpen && !uiState.isPeopleOpen && !uiState.isMomentsOpen && !uiState.isSearchOpen && uiState.selectedAlbum == null && uiState.selectedPerson == null) {
            GalleryFab(onClick = { }, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 92.dp))
            NavigationPill(
                selectedTab = if (pagerState.currentPage == 0) GalleryTab.PHOTOS else GalleryTab.ALBUMS,
                onTabSelected = { tab ->
                    scope.launch { pagerState.animateScrollToPage(if (tab == GalleryTab.PHOTOS) 0 else 1) }
                    viewModel.setCurrentTab(tab)
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
fun GalleryFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(onClick = onClick, containerColor = Color(0xFF0058bc), contentColor = Color.White, modifier = modifier) {
        Icon(imageVector = Icons.Default.Add, contentDescription = "Adicionar")
    }
}
