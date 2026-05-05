package com.hypergallery.viewmodel

import android.app.Application
import android.graphics.Rect
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hypergallery.data.Album
import com.hypergallery.data.AlbumType
import com.hypergallery.data.MediaFilter
import com.hypergallery.data.MediaGroup
import com.hypergallery.data.MediaItem
import com.hypergallery.data.MediaRepository
import com.hypergallery.data.MetadataRepository
import com.hypergallery.data.ImageMetadata
import com.hypergallery.ui.components.GalleryTab
import com.hypergallery.ui.components.MediaFilterChip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.hypergallery.data.VisualAnalysisRepository
import com.hypergallery.data.AnalysisResult

data class GalleryUiState(
    val isLoading: Boolean = true,
    val mediaGroups: List<MediaGroup> = emptyList(),
    val mainAlbums: List<Album> = emptyList(),
    val otherAlbums: List<Album> = emptyList(),
    val favoriteAlbum: Album? = null,
    val showAllAlbums: Boolean = false,
    val selectedAlbum: Album? = null,
    val albumMedia: List<MediaGroup> = emptyList(),
    val isAlbumLoading: Boolean = false,
    val viewerMedia: List<MediaItem> = emptyList(),
    val viewerStartIndex: Int = 0,
    val lastViewerIndex: Int = -1,
    val albumScrollPosition: Int = 0,
    val isViewerOpen: Boolean = false,
    val isDetailsOpen: Boolean = false,
    val currentFolderPath: String? = null,
    val currentFolderTitle: String? = null,
    val selectedFilter: MediaFilterChip = MediaFilterChip.CAMERA,
    val selectedItems: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false,
    val isDarkMode: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val isWhatsAppParserEnabled: Boolean = false,
    val saveExifData: Boolean = true,
    val whatsappBackupName: String? = null,
    val importProgress: Float? = null,
    val albumSort: com.hypergallery.data.AlbumSort = com.hypergallery.data.AlbumSort.DATE_DESC,
    val currentTab: GalleryTab = GalleryTab.PHOTOS,
    val searchSuggestions: List<String> = emptyList(),
    val userVirtualAlbums: Map<String, List<Long>> = emptyMap(),
    val peopleAndPets: List<MediaItem> = emptyList(),
    val peopleWithFaces: List<Pair<com.hypergallery.data.Person, List<com.hypergallery.data.FaceInfo>>> = emptyList(),
    val mediaLabels: Map<Long, List<String>> = emptyMap(),
    val isPeopleOpen: Boolean = false,
    val isMomentsOpen: Boolean = false,
    val isSearchOpen: Boolean = false,
    val selectedPerson: com.hypergallery.data.Person? = null,
    val personMedia: List<MediaGroup> = emptyList(),
    val searchResults: List<MediaGroup> = emptyList(),
    val analysisProgress: Float = 0f,
    val analyzedCount: Int = 0,
    val totalToAnalyze: Int = 0,
    val isAnalyzing: Boolean = false,
    val isOptimizing: Boolean = false,
    val isManualFaceSelectionMode: Boolean = false,
    val optimizationStatus: String = "",
    val isViewingFromPerson: Boolean = false,
    val selectingCoverForAlbumId: Long? = null,
    val currentMediaFaces: List<Triple<com.hypergallery.data.FaceInfo, String?, String?>> = emptyList(),
    val pendingFolderPath: String? = null,
    val error: String? = null,
    // Novo: status do modelo de faces
    val faceModelAvailable: Boolean = false,
    val faceAnalysisStatus: String = ""
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "GalleryViewModel"
        // Atualiza UI a cada N itens durante análise
        private const val ANALYSIS_UI_UPDATE_EVERY = 5
        // Pausa entre análises para não sobrecarregar CPU
        private const val ANALYSIS_DELAY_MS = 30L
        // Pausa ao detectar memória alta
        private const val MEMORY_PRESSURE_DELAY_MS = 4000L
        // Limiar de memória em MB para pausar análise
        private const val MEMORY_THRESHOLD_MB = 400
    }

    private val repository = MediaRepository(application)
    private val metadataRepository = MetadataRepository(application)
    private val visualAnalysisRepository = VisualAnalysisRepository(application)
    private val faceRepository = visualAnalysisRepository.faceRepository
    private val prefs = application.getSharedPreferences("hypergallery_prefs", android.content.Context.MODE_PRIVATE)

    private val analysisMutex = Mutex()
    private var analysisJob: kotlinx.coroutines.Job? = null

    private val _uiState = MutableStateFlow(
        GalleryUiState(
            isWhatsAppParserEnabled = prefs.getBoolean("whatsapp_parser_enabled", false),
            isDarkMode = prefs.getBoolean("dark_mode", false),
            saveExifData = prefs.getBoolean("save_exif_data", true),
            whatsappBackupName = repository.getSavedBackupName(),
            faceModelAvailable = false // será atualizado após init
        )
    )
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init {
        loadMedia()
        // Atualiza status do modelo
        _uiState.value = _uiState.value.copy(
            faceModelAvailable = visualAnalysisRepository.hasFaceNetModel
        )
        // Carrega pessoas já salvas
        refreshPeopleFromCache()
        // Inicia análise em background
        startBackgroundAnalysis()
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  ANÁLISE DE ROSTOS EM BACKGROUND
    // ──────────────────────────────────────────────────────────────────────────

    fun startBackgroundAnalysis() {
        if (analysisJob?.isActive == true) {
            Log.d(TAG, "Analysis already running")
            return
        }

        analysisJob = viewModelScope.launch(Dispatchers.Default) {
            analysisMutex.withLock {
                try {
                    runAnalysisLoop()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    Log.d(TAG, "Analysis cancelled")
                    _uiState.value = _uiState.value.copy(isAnalyzing = false)
                } catch (e: Exception) {
                    Log.e(TAG, "Analysis error", e)
                    _uiState.value = _uiState.value.copy(isAnalyzing = false, error = e.message)
                }
            }
        }
    }

    private suspend fun runAnalysisLoop() {
        Log.d(TAG, "--- Analysis loop started ---")

        // Espera o carregamento inicial terminar
        kotlinx.coroutines.delay(1500)
        var waited = 0
        while (_uiState.value.isLoading && waited < 30_000) {
            kotlinx.coroutines.delay(500)
            waited += 500
        }

        // ── Coleta TODAS as mídias para analisar ──────────────────────────
        // MUDANÇA CRÍTICA: Analisa TODAS as mídias visíveis, não só as de álbuns
        // com "isAnalysisEnabled". O usuário não sabe que precisa ativar esse toggle.
        val allMedia = withContext(Dispatchers.IO) {
            val items = mutableListOf<MediaItem>()

            // Pega todos os grupos já carregados na UI
            val fromGroups = _uiState.value.mediaGroups.flatMap { it.items }
            items.addAll(fromGroups)

            // Se estiver vazio, busca diretamente
            if (items.isEmpty()) {
                items.addAll(repository.getAllMedia(MediaFilter.ALL))
            }

            items.distinctBy { it.id }.filter { it.isPhoto } // Só fotos para detecção de rostos
        }

        val analyzedIds = withContext(Dispatchers.IO) {
            visualAnalysisRepository.getAllAnalyzedIds().toSet()
        }
        val toAnalyze = allMedia.filter { it.id !in analyzedIds }

        if (toAnalyze.isEmpty()) {
            Log.d(TAG, "Nothing new to analyze")
            _uiState.value = _uiState.value.copy(isAnalyzing = false, faceAnalysisStatus = "")
            return
        }

        Log.d(TAG, "Will analyze ${toAnalyze.size} images")
        _uiState.value = _uiState.value.copy(
            isAnalyzing = true,
            totalToAnalyze = toAnalyze.size,
            analyzedCount = 0,
            analysisProgress = 0f,
            faceAnalysisStatus = "Iniciando análise de ${toAnalyze.size} fotos..."
        )

        val runtime = Runtime.getRuntime()

        toAnalyze.forEachIndexed { index, item ->
            if (!analysisJob!!.isActive) return@forEachIndexed

            // Verificação de memória a cada 15 itens
            if (index % 15 == 0) {
                val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576L
                if (usedMb > MEMORY_THRESHOLD_MB) {
                    Log.w(TAG, "Memory pressure ($usedMb MB), pausing...")
                    System.gc()
                    kotlinx.coroutines.delay(MEMORY_PRESSURE_DELAY_MS)
                }
            }

            try {
                withContext(Dispatchers.IO) {
                    visualAnalysisRepository.analyzeImage(item.uri, item.id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing ${item.id}: ${e.message}")
            }

            // Atualiza UI a cada N itens
            val isLast = index == toAnalyze.size - 1
            if (index % ANALYSIS_UI_UPDATE_EVERY == 0 || isLast) {
                val progress = (index + 1).toFloat() / toAnalyze.size
                _uiState.value = _uiState.value.copy(
                    analyzedCount = index + 1,
                    analysisProgress = progress,
                    faceAnalysisStatus = "${index + 1}/${toAnalyze.size} fotos analisadas"
                )
                // Atualiza pessoas na UI
                refreshPeopleFromCache()
            }

            kotlinx.coroutines.delay(ANALYSIS_DELAY_MS)
        }

        Log.d(TAG, "--- Analysis loop finished ---")
        _uiState.value = _uiState.value.copy(
            isAnalyzing = false,
            analysisProgress = 1.0f,
            faceAnalysisStatus = "Análise concluída — ${faceRepository.getPersonCount()} pessoas identificadas"
        )

        // Limpa o status após 5s
        kotlinx.coroutines.delay(5000)
        _uiState.value = _uiState.value.copy(faceAnalysisStatus = "")

        refreshPeopleFromCache()
    }

    /**
     * Força re-análise de todas as imagens (útil quando o usuário instalar o modelo).
     */
    fun reanalyzeAll() {
        analysisJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            // Limpa resultados anteriores
            val allMedia = _uiState.value.mediaGroups.flatMap { it.items }
            allMedia.forEach { item ->
                visualAnalysisRepository.reanalyzeImage(item.uri, item.id)
            }
            faceRepository.clearAll()
            withContext(Dispatchers.Main) {
                refreshPeopleFromCache()
                startBackgroundAnalysis()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  ATUALIZAÇÃO DE PESSOAS NA UI
    // ──────────────────────────────────────────────────────────────────────────

    private fun refreshPeopleFromCache() {
        viewModelScope.launch(Dispatchers.Default) {
            val people = faceRepository.getAllPeople()
            val allMedia = _uiState.value.mediaGroups.flatMap { it.items }

            val allLabels = mutableSetOf<String>()
            val mediaLabelsMap = mutableMapOf<Long, List<String>>()
            val mediaWithFaces = mutableListOf<MediaItem>()

            allMedia.forEach { item ->
                val labels = visualAnalysisRepository.getLabels(item.id)
                if (labels.isNotEmpty()) {
                    allLabels.addAll(labels)
                    mediaLabelsMap[item.id] = labels
                }
                if (visualAnalysisRepository.getFaceCount(item.id) > 0 ||
                    visualAnalysisRepository.hasPets(item.id)) {
                    mediaWithFaces.add(item)
                }
            }

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    peopleWithFaces = people,
                    peopleAndPets = mediaWithFaces.take(30),
                    searchSuggestions = allLabels.toList().take(20),
                    mediaLabels = mediaLabelsMap
                )
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  PESSOAS
    // ──────────────────────────────────────────────────────────────────────────

    fun openPeople() {
        refreshPeopleFromCache()
        _uiState.value = _uiState.value.copy(isPeopleOpen = true)
    }
    fun closePeople() { _uiState.value = _uiState.value.copy(isPeopleOpen = false, selectedPerson = null) }

    fun selectPerson(person: com.hypergallery.data.Person) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedPerson = person, isAlbumLoading = true)
            val personFaces = withContext(Dispatchers.Default) {
                faceRepository.getAllPeople().find { it.first.id == person.id }?.second ?: emptyList()
            }
            val mediaIds = personFaces.map { it.mediaId }.toSet()
            val allMedia = _uiState.value.mediaGroups.flatMap { it.items }
            val filteredMedia = allMedia.filter { it.id in mediaIds }
            val groups = withContext(Dispatchers.Default) { repository.groupByDate(filteredMedia) }
            _uiState.value = _uiState.value.copy(personMedia = groups, isAlbumLoading = false)
        }
    }

    fun clearSelectedPerson() {
        _uiState.value = _uiState.value.copy(selectedPerson = null, personMedia = emptyList())
    }

    fun renamePerson(personId: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            faceRepository.renamePerson(personId, newName)
            withContext(Dispatchers.Main) { refreshPeopleFromCache() }
        }
    }

    fun mergePeople(targetId: String, sourceIds: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            faceRepository.mergePeople(targetId, sourceIds)
            withContext(Dispatchers.Main) { refreshPeopleFromCache() }
        }
    }

    fun optimizePeople() {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.value = _uiState.value.copy(isOptimizing = true, optimizationStatus = "Analisando grupos...")
            faceRepository.autoMergePeople { status ->
                _uiState.value = _uiState.value.copy(optimizationStatus = status)
            }
            withContext(Dispatchers.Main) { refreshPeopleFromCache() }
            _uiState.value = _uiState.value.copy(isOptimizing = false)
            kotlinx.coroutines.delay(3000)
            _uiState.value = _uiState.value.copy(optimizationStatus = "")
        }
    }

    fun deletePerson(personId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            faceRepository.deletePerson(personId)
            withContext(Dispatchers.Main) { refreshPeopleFromCache() }
        }
    }

    fun removeFace(personId: String, faceId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            faceRepository.removeFaceFromPerson(personId, faceId)
            withContext(Dispatchers.Main) { refreshPeopleFromCache() }
        }
    }

    fun searchFacesForPerson(personId: String) { optimizePeople() }

    fun startManualFaceSelection() { _uiState.value = _uiState.value.copy(isManualFaceSelectionMode = true) }
    fun cancelManualFaceSelection() { _uiState.value = _uiState.value.copy(isManualFaceSelectionMode = false) }

    fun updateFaceBounds(faceId: String, newBounds: Rect) {
        faceRepository.updateFaceBounds(faceId, newBounds)
        refreshPeopleFromCache()
        _uiState.value = _uiState.value.copy(isManualFaceSelectionMode = false)
    }

    fun getFacesForMedia(mediaId: Long) {
        viewModelScope.launch(Dispatchers.Default) {
            val faces = faceRepository.getFacesForMedia(mediaId)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(currentMediaFaces = faces)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  BUSCA
    // ──────────────────────────────────────────────────────────────────────────

    fun openSearch() { _uiState.value = _uiState.value.copy(isSearchOpen = true, searchResults = emptyList()) }
    fun closeSearch() { _uiState.value = _uiState.value.copy(isSearchOpen = false, searchResults = emptyList()) }

    fun onSearchQueryChanged(query: String) {
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            val allMedia = _uiState.value.mediaGroups.flatMap { it.items }
            val lq = query.lowercase()
            val results = allMedia.filter { item ->
                item.name.lowercase().contains(lq) ||
                item.bucketName.lowercase().contains(lq) ||
                visualAnalysisRepository.getLabels(item.id).any { it.lowercase().contains(lq) } ||
                visualAnalysisRepository.getText(item.id).lowercase().contains(lq)
            }
            val grouped = repository.groupByDate(results)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(searchResults = grouped)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  MOMENTOS
    // ──────────────────────────────────────────────────────────────────────────

    fun openMoments() { _uiState.value = _uiState.value.copy(isMomentsOpen = true) }
    fun closeMoments() { _uiState.value = _uiState.value.copy(isMomentsOpen = false) }

    // ──────────────────────────────────────────────────────────────────────────
    //  MÍDIA
    // ──────────────────────────────────────────────────────────────────────────

    fun loadMedia() {
        repository.invalidateCache()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val albumsData = withContext(Dispatchers.IO) {
                    val allAlbums = repository.getAlbums()
                    val favorites = repository.getFavorites()
                    val main = allAlbums.filter {
                        it.isPinned || it.isVirtualFolder ||
                        it.type == AlbumType.CAMERA || it.type == AlbumType.DOWNLOADS
                    }
                    val other = allAlbums.filter { it !in main && it.type != AlbumType.FAVORITES }
                    val fav = if (favorites.isNotEmpty())
                        Album(-1, "Favoritos", favorites.first().uri, favorites.size, -1, AlbumType.FAVORITES)
                    else null
                    Triple(main, other, fav)
                }

                _uiState.value = _uiState.value.copy(
                    mainAlbums = albumsData.first,
                    otherAlbums = albumsData.second,
                    favoriteAlbum = albumsData.third,
                    userVirtualAlbums = repository.getUserVirtualAlbums()
                )

                withContext(Dispatchers.IO) {
                    val filter = _uiState.value.selectedFilter.toMediaFilter()

                    // Rápido: primeiros 100
                    val initial = repository.getAllMedia(filter, limit = 100)
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            mediaGroups = repository.groupByDate(initial),
                            isLoading = false
                        )
                    }

                    // Completo em background
                    val all = repository.getAllMedia(filter)
                    val groups = repository.groupByDate(all)
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(mediaGroups = groups)
                        // Atualiza pessoas com a lista completa
                        refreshPeopleFromCache()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading media", e)
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    private fun MediaFilterChip.toMediaFilter() = when (this) {
        MediaFilterChip.ALL -> MediaFilter.ALL
        MediaFilterChip.CAMERA -> MediaFilter.CAMERA
        MediaFilterChip.FAVORITES -> MediaFilter.FAVORITES
        MediaFilterChip.VIDEOS -> MediaFilter.VIDEOS
        MediaFilterChip.SCREENSHOTS -> MediaFilter.SCREENSHOTS
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  CONFIGURAÇÕES
    // ──────────────────────────────────────────────────────────────────────────

    fun toggleDarkMode() {
        val v = !_uiState.value.isDarkMode
        prefs.edit().putBoolean("dark_mode", v).apply()
        _uiState.value = _uiState.value.copy(isDarkMode = v)
    }

    fun toggleWhatsAppParser() {
        val v = !_uiState.value.isWhatsAppParserEnabled
        prefs.edit().putBoolean("whatsapp_parser_enabled", v).apply()
        _uiState.value = _uiState.value.copy(isWhatsAppParserEnabled = v)
        if (v) repository.loadWhatsAppMapping()
        loadMedia()
    }

    fun toggleSaveExif() {
        val v = !_uiState.value.saveExifData
        prefs.edit().putBoolean("save_exif_data", v).apply()
        _uiState.value = _uiState.value.copy(saveExifData = v)
    }

    fun importWhatsAppBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val fileName = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx != -1 && c.moveToFirst()) c.getString(idx) else null
                } ?: "backup.txt"

                _uiState.value = _uiState.value.copy(whatsappBackupName = fileName, importProgress = 0.1f)

                val content = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: ""
                }
                _uiState.value = _uiState.value.copy(importProgress = 0.4f)

                val mapping = withContext(Dispatchers.Default) {
                    com.hypergallery.util.WhatsAppParser.parseWhatsAppBackup(content)
                }
                _uiState.value = _uiState.value.copy(importProgress = 0.7f)

                withContext(Dispatchers.IO) { repository.setWhatsAppMapping(mapping, fileName) }
                _uiState.value = _uiState.value.copy(isWhatsAppParserEnabled = true, importProgress = 1.0f)
                loadMedia()
                kotlinx.coroutines.delay(1000)
                _uiState.value = _uiState.value.copy(importProgress = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Erro ao importar: ${e.message}", importProgress = null)
            }
        }
    }

    fun openSettings() { _uiState.value = _uiState.value.copy(isSettingsOpen = true) }
    fun closeSettings() { _uiState.value = _uiState.value.copy(isSettingsOpen = false) }
    fun toggleShowAllAlbums() { _uiState.value = _uiState.value.copy(showAllAlbums = !_uiState.value.showAllAlbums) }

    // ──────────────────────────────────────────────────────────────────────────
    //  ÁLBUNS
    // ──────────────────────────────────────────────────────────────────────────

    fun selectAlbum(album: Album) {
        val isGroupingFolder = album.isVirtualFolder &&
            (album.bucketId == -3L || album.bucketId == -888L || album.bucketId == -2L ||
             album.bucketId == -4L || album.bucketId == -5L || album.bucketId == -6L)

        if (isGroupingFolder) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    isLoading = true, currentFolderPath = album.relativePath, currentFolderTitle = album.name
                )
                val sub = withContext(Dispatchers.IO) {
                    repository.getAlbums(album.relativePath, sort = _uiState.value.albumSort)
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    mainAlbums = sub.filter { it.type != AlbumType.FOLDER && it.type != AlbumType.FAVORITES },
                    otherAlbums = sub.filter { it.type == AlbumType.FOLDER },
                    favoriteAlbum = null
                )
            }
            return
        }

        viewModelScope.launch {
            val newScrollPos = if (_uiState.value.selectedAlbum?.id != album.id) 0 else _uiState.value.albumScrollPosition
            _uiState.value = _uiState.value.copy(
                selectedAlbum = album, isAlbumLoading = true, albumMedia = emptyList(), albumScrollPosition = newScrollPos
            )
            try {
                val media = withContext(Dispatchers.IO) {
                    val items = when {
                        album.id == -1L -> repository.getFavorites()
                        album.id == -999L || album.bucketId == -999L -> repository.getBackupMedia()
                        album.relativePath.startsWith("whatsapp/contacts/") -> repository.getMediaByContact(album.name)
                        else -> repository.getMediaByBucket(album.bucketId)
                    }
                    items.map { it.copy(text = visualAnalysisRepository.getText(it.id)) }
                }
                val groups = withContext(Dispatchers.Default) { repository.groupByDate(media) }
                _uiState.value = _uiState.value.copy(isAlbumLoading = false, albumMedia = groups)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading album", e)
                _uiState.value = _uiState.value.copy(isAlbumLoading = false)
            }
        }
    }

    fun clearSelectedAlbum() {
        val scroll = _uiState.value.albumScrollPosition
        if (_uiState.value.selectedAlbum == null && _uiState.value.currentFolderPath != null) {
            _uiState.value = _uiState.value.copy(currentFolderPath = null, currentFolderTitle = null)
            loadMedia(); return
        }
        _uiState.value = _uiState.value.copy(selectedAlbum = null, albumMedia = emptyList(), albumScrollPosition = scroll)
    }

    fun renameAlbum(bucketId: Long, newName: String) { repository.renameAlbum(bucketId, newName); loadMedia() }
    fun createAlbum(name: String) { repository.createAlbum(name, _uiState.value.currentFolderPath); loadMedia() }
    fun setAlbumCover(bucketId: Long, uri: android.net.Uri) {
        repository.setAlbumCover(bucketId, uri)
        _uiState.value = _uiState.value.copy(selectingCoverForAlbumId = null)
        loadMedia()
    }
    fun startSelectingCover(bucketId: Long) {
        (_uiState.value.mainAlbums + _uiState.value.otherAlbums).find { it.bucketId == bucketId }?.let {
            _uiState.value = _uiState.value.copy(selectingCoverForAlbumId = bucketId)
            selectAlbum(it)
        }
    }
    fun cancelSelectingCover() { _uiState.value = _uiState.value.copy(selectingCoverForAlbumId = null) }
    fun toggleAlbumAnalysis(bucketId: Long, enabled: Boolean) {
        repository.setAlbumAnalysisEnabled(bucketId, enabled)
        loadMedia(); startBackgroundAnalysis()
    }
    fun toggleAlbumPinned(bucketId: Long, pinned: Boolean) { repository.setAlbumPinned(bucketId, pinned); loadMedia() }
    fun addAlbumToVirtual(name: String, id: Long) { repository.addAlbumToVirtual(name, id); loadMedia() }
    fun createVirtualAlbum(name: String, id: Long) { repository.addAlbumToVirtual(name, id); loadMedia() }
    fun removeAlbumFromVirtual(name: String, id: Long) { repository.removeAlbumFromVirtual(name, id); loadMedia() }
    fun deleteVirtualAlbum(name: String) { repository.deleteVirtualAlbum(name); loadMedia() }
    fun createVirtualAlbumFromFolder(name: String, path: String) { repository.addVirtualFolderRoot(name, path); loadMedia() }
    fun deleteVirtualFolderRoot(name: String) { repository.deleteVirtualFolderRoot(name); loadMedia() }
    fun setAlbumSort(sort: com.hypergallery.data.AlbumSort) { _uiState.value = _uiState.value.copy(albumSort = sort); loadMedia() }
    fun setPendingFolder(path: String) { _uiState.value = _uiState.value.copy(pendingFolderPath = path) }
    fun clearPendingFolder() { _uiState.value = _uiState.value.copy(pendingFolderPath = null) }

    // ──────────────────────────────────────────────────────────────────────────
    //  VIEWER
    // ──────────────────────────────────────────────────────────────────────────

    fun openViewer(mediaList: List<MediaItem>, index: Int = 0, useLastPosition: Boolean = true, fromPerson: Boolean = false) {
        val startIndex = if (useLastPosition && index == 0 && _uiState.value.lastViewerIndex > 0)
            _uiState.value.lastViewerIndex.coerceAtMost(mediaList.size - 1) else index
        viewModelScope.launch(Dispatchers.Default) {
            val enriched = mediaList.map { it.copy(text = visualAnalysisRepository.getText(it.id)) }
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    viewerMedia = enriched, viewerStartIndex = startIndex,
                    isViewerOpen = true, isViewingFromPerson = fromPerson, lastViewerIndex = -1
                )
            }
        }
    }

    fun openViewerWithScroll(mediaList: List<MediaItem>, startIndex: Int, scrollPosition: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            val enriched = mediaList.map { it.copy(text = visualAnalysisRepository.getText(it.id)) }
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    viewerMedia = enriched, viewerStartIndex = startIndex,
                    albumScrollPosition = scrollPosition, isViewerOpen = true,
                    isViewingFromPerson = false, lastViewerIndex = -1
                )
            }
        }
    }

    fun closeViewer(currentIndex: Int = 0) {
        _uiState.value = _uiState.value.copy(
            isViewerOpen = false, lastViewerIndex = currentIndex, currentMediaFaces = emptyList()
        )
    }

    fun openDetails() { _uiState.value = _uiState.value.copy(isDetailsOpen = true) }
    fun closeDetails() { _uiState.value = _uiState.value.copy(isDetailsOpen = false) }
    fun saveScrollPosition(position: Int) { _uiState.value = _uiState.value.copy(albumScrollPosition = position) }
    fun setCurrentTab(tab: GalleryTab) { _uiState.value = _uiState.value.copy(currentTab = tab) }

    fun goToMediaInAlbum(mediaItem: MediaItem) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isViewerOpen = false, isDetailsOpen = false)
            setCurrentTab(GalleryTab.ALBUMS)
            kotlinx.coroutines.delay(50)
            val allBuckets = withContext(Dispatchers.IO) { repository.getAlbums(null) }
            val album = allBuckets.find { it.bucketId == mediaItem.bucketId }
            if (album != null) {
                selectAlbum(album)
                var waited = 0
                while (_uiState.value.isAlbumLoading && waited < 5000) { kotlinx.coroutines.delay(100); waited += 100 }
                var idx = 0; var found = false
                for (group in _uiState.value.albumMedia) {
                    if (group.items.any { it.id == mediaItem.id }) {
                        idx += group.items.indexOfFirst { it.id == mediaItem.id } + 1; found = true; break
                    }
                    idx += group.items.size + 1
                }
                if (found) _uiState.value = _uiState.value.copy(albumScrollPosition = idx)
            } else {
                _uiState.value = _uiState.value.copy(selectedAlbum = null, currentTab = GalleryTab.PHOTOS)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  SELEÇÃO / DELETE
    // ──────────────────────────────────────────────────────────────────────────

    fun toggleSelection(mediaId: Long) {
        val s = _uiState.value.selectedItems.toMutableSet()
        if (!s.add(mediaId)) s.remove(mediaId)
        _uiState.value = _uiState.value.copy(selectedItems = s, isSelectionMode = s.isNotEmpty())
    }

    fun clearSelection() { _uiState.value = _uiState.value.copy(selectedItems = emptySet(), isSelectionMode = false) }

    fun deleteSelectedMedia() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value.selectedItems.forEach { repository.moveToTrash(it) }
            withContext(Dispatchers.Main) { clearSelection() }
        }
    }

    fun deleteSingleMedia(mediaItem: MediaItem) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.moveToTrash(mediaItem.id)
            val updated = _uiState.value.viewerMedia.filter { it.id != mediaItem.id }
            withContext(Dispatchers.Main) { _uiState.value = _uiState.value.copy(viewerMedia = updated) }
        }
    }

    fun toggleFavorite(mediaItem: MediaItem) { Log.d(TAG, "toggleFavorite ${mediaItem.id}") }

    fun openTrash() {
        viewModelScope.launch {
            val trash = withContext(Dispatchers.IO) { repository.getTrashMedia() }
            if (trash.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isViewerOpen = true, viewerMedia = trash, viewerStartIndex = 0,
                    selectedAlbum = Album(-100, "Lixeira", android.net.Uri.EMPTY, trash.size, -100, AlbumType.FOLDER)
                )
            }
        }
    }

    fun restoreFromTrash(mediaId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.restoreFromTrash(mediaId)
            val trash = repository.getTrashMedia()
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    viewerMedia = trash,
                    selectedAlbum = _uiState.value.selectedAlbum?.copy(itemCount = trash.size)
                )
            }
        }
    }

    fun permanentlyDelete(mediaId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePermanently(mediaId)
            repository.restoreFromTrash(mediaId)
            val trash = repository.getTrashMedia()
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    viewerMedia = trash,
                    selectedAlbum = _uiState.value.selectedAlbum?.copy(itemCount = trash.size)
                )
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.emptyTrash()
            withContext(Dispatchers.Main) { _uiState.value = _uiState.value.copy(isViewerOpen = false, viewerMedia = emptyList()) }
        }
    }

    fun setFilter(filter: MediaFilterChip) { _uiState.value = _uiState.value.copy(selectedFilter = filter); loadMedia() }

    // ──────────────────────────────────────────────────────────────────────────
    //  METADADOS
    // ──────────────────────────────────────────────────────────────────────────

    fun extractTextFromMedia(mediaItem: MediaItem, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val saved = visualAnalysisRepository.getText(mediaItem.id)
            if (saved.isNotBlank()) { onResult(saved); return@launch }
            val result = withContext(Dispatchers.IO) {
                visualAnalysisRepository.analyzeImage(mediaItem.uri, mediaItem.id)
            }
            onResult(result.text)
        }
    }

    fun renameMedia(mediaItem: MediaItem, newName: String) { Log.d(TAG, "rename ${mediaItem.name} -> $newName") }

    fun getMetadata(mediaId: Long): ImageMetadata = metadataRepository.getMetadata(mediaId)

    fun saveMetadata(mediaId: Long, description: String, tags: List<String>) {
        metadataRepository.saveMetadata(mediaId, description, tags)
    }

    fun readExifFromUri(uri: android.net.Uri): com.hypergallery.data.ExifData? =
        metadataRepository.readExifFromUri(getApplication(), uri)

    fun getExifData(uri: android.net.Uri): com.hypergallery.data.ExifData? =
        try { metadataRepository.readExifFromUri(getApplication(), uri) } catch (e: Exception) { null }
}
