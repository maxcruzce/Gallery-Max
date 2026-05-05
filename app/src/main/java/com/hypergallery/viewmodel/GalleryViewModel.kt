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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
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
    val isFaceRecognitionEnabled: Boolean = true,
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
    val albumSearchResults: List<Album> = emptyList(),
    val analysisProgress: Float = 0f,
    val analyzedCount: Int = 0,
    val totalToAnalyze: Int = 0,
    val isAnalyzing: Boolean = false,
    val isOptimizing: Boolean = false,
    val isManualFaceSelectionMode: Boolean = false,
    val optimizationStatus: String = "",
    val faceAnalysisStatus: String = "",
    val faceModelAvailable: Boolean = false,
    val isViewingFromPerson: Boolean = false,
    val selectingCoverForAlbumId: Long? = null,
    val currentMediaFaces: List<Triple<com.hypergallery.data.FaceInfo, String?, String?>> = emptyList(),
    val pendingFolderPath: String? = null,
    val error: String? = null
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "GalleryViewModel"
        // PERF: Intervalo mínimo entre atualizações de análise (ms)
        private const val ANALYSIS_UPDATE_INTERVAL_MS = 5
        // PERF: Atualiza a UI de análise a cada N itens, não a cada 1
        private const val ANALYSIS_UI_UPDATE_EVERY = 10
    }

    private val repository = MediaRepository(application)
    private val metadataRepository = MetadataRepository(application)
    private val visualAnalysisRepository = VisualAnalysisRepository(application)
    private val faceRepository = com.hypergallery.data.FaceRepository(application)
    private val prefs = application.getSharedPreferences("hypergallery_prefs", android.content.Context.MODE_PRIVATE)

    private val analysisMutex = Mutex()
    private var analysisJob: kotlinx.coroutines.Job? = null

    private val _uiState = MutableStateFlow(
        GalleryUiState(
            isWhatsAppParserEnabled = prefs.getBoolean("whatsapp_parser_enabled", false),
            isDarkMode = prefs.getBoolean("dark_mode", false),
            saveExifData = prefs.getBoolean("save_exif_data", true),
            isFaceRecognitionEnabled = prefs.getBoolean("face_recognition_enabled", true),
            whatsappBackupName = repository.getSavedBackupName()
        )
    )
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init {
        loadMedia()
        _uiState.value = _uiState.value.copy(
            faceModelAvailable = visualAnalysisRepository.hasFaceNetModel
        )
        startBackgroundAnalysis()
    }

    fun startBackgroundAnalysis() {
        if (analysisJob?.isActive == true) {
            Log.d(TAG, "Background analysis already running.")
            return
        }

        if (!_uiState.value.isFaceRecognitionEnabled) {
            Log.d(TAG, "Face recognition disabled, skipping analysis.")
            return
        }

        analysisJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                analysisMutex.withLock {
                    Log.d(TAG, "--- Analysis Loop Started ---")

                    // Aguarda UI parar de carregar
                    kotlinx.coroutines.delay(2000)
                    while (_uiState.value.isLoading) {
                        if (!isActive) return@withLock
                        kotlinx.coroutines.delay(500) // BUG FIX: delay reduzido de 1000ms para 500ms
                        Log.d(TAG, "Waiting for UI to stop loading...")
                    }

                    _uiState.value = _uiState.value.copy(optimizationStatus = "Buscando mídias para analisar...")

                    val allAlbums = repository.getAlbums()
                    val enabledBuckets = allAlbums.filter { it.isAnalysisEnabled }

                    if (enabledBuckets.isEmpty()) {
                        Log.d(TAG, "No albums enabled for analysis.")
                        _uiState.value = _uiState.value.copy(isAnalyzing = false, optimizationStatus = "")
                        return@withLock
                    }

                    val allMediaToScan = mutableListOf<MediaItem>()
                    enabledBuckets.forEach { album ->
                        if (!isActive) return@withLock
                        allMediaToScan.addAll(repository.getMediaByBucket(album.bucketId))
                    }

                    val analyzedIds = visualAnalysisRepository.getAllAnalyzedIds().toSet()
                    val toAnalyze = allMediaToScan.filter { it.id !in analyzedIds }.distinctBy { it.id }

                    if (toAnalyze.isEmpty()) {
                        Log.d(TAG, "Nothing to analyze.")
                        _uiState.value = _uiState.value.copy(isAnalyzing = false, optimizationStatus = "")
                        return@withLock
                    }

                    _uiState.value = _uiState.value.copy(
                        totalToAnalyze = toAnalyze.size,
                        analyzedCount = 0,
                        isAnalyzing = true,
                        optimizationStatus = ""
                    )

                    val runtime = Runtime.getRuntime()

                    toAnalyze.forEachIndexed { index, item ->
                        if (!isActive) return@withLock

                        Log.d(TAG, "Analyzing ${index + 1}/${toAnalyze.size}: ${item.name}")

                        // PERF: Verifica memória a cada 20 itens para reduzir overhead
                        if (index % 20 == 0) {
                            val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576
                            if (usedMemMb > 450) {
                                Log.w(TAG, "High memory ($usedMemMb MB), GC + pause")
                                System.gc()
                                kotlinx.coroutines.delay(3000)
                            }
                        }

                        try {
                            visualAnalysisRepository.analyzeImage(item.uri, item.id)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error analyzing ${item.id}", e)
                        }

                        // PERF: Atualiza UI a cada ANALYSIS_UI_UPDATE_EVERY itens ou no último
                        val isLast = index == toAnalyze.size - 1
                        if (index % ANALYSIS_UI_UPDATE_EVERY == 0 || isLast) {
                            _uiState.value = _uiState.value.copy(
                                analyzedCount = index + 1,
                                analysisProgress = (index + 1).toFloat() / toAnalyze.size
                            )
                            updateSearchSuggestionsAndPeople()
                        }

                        kotlinx.coroutines.delay(ANALYSIS_UPDATE_INTERVAL_MS.toLong())
                    }

                    Log.d(TAG, "--- Analysis Loop Finished ---")
                    _uiState.value = _uiState.value.copy(isAnalyzing = false, analysisProgress = 1.0f)
                    updateSearchSuggestionsAndPeople()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.d(TAG, "Analysis job cancelled")
                _uiState.value = _uiState.value.copy(isAnalyzing = false)
            }
        }
    }

    fun openPeople() { _uiState.value = _uiState.value.copy(isPeopleOpen = true) }
    fun closePeople() { _uiState.value = _uiState.value.copy(isPeopleOpen = false, selectedPerson = null) }

    fun selectPerson(person: com.hypergallery.data.Person) {
        viewModelScope.launch {
            // BUG FIX: Não reusa isLoading global para sub-operações — usa isAlbumLoading
            _uiState.value = _uiState.value.copy(selectedPerson = person, isAlbumLoading = true)
            val allMedia = _uiState.value.mediaGroups.flatMap { it.items }
            val personFaces = faceRepository.getAllPeople().find { it.first.id == person.id }?.second ?: emptyList()
            val mediaIds = personFaces.map { it.mediaId }.toSet()
            val filteredMedia = allMedia.filter { it.id in mediaIds }
            val groups = withContext(Dispatchers.Default) { repository.groupByDate(filteredMedia) }
            _uiState.value = _uiState.value.copy(personMedia = groups, isAlbumLoading = false)
        }
    }

    fun clearSelectedPerson() {
        _uiState.value = _uiState.value.copy(selectedPerson = null, personMedia = emptyList())
    }

    fun openSearch() { _uiState.value = _uiState.value.copy(isSearchOpen = true, searchResults = emptyList()) }
    fun closeSearch() { _uiState.value = _uiState.value.copy(isSearchOpen = false, searchResults = emptyList()) }

    fun onSearchQueryChanged(query: String) {
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList(), albumSearchResults = emptyList())
            return
        }
        // PERF: Busca em background para não travar a UI
        viewModelScope.launch(Dispatchers.Default) {
            val allMedia = _uiState.value.mediaGroups.flatMap { it.items }
            val results = allMedia.filter { item ->
                item.name.contains(query, ignoreCase = true) ||
                item.bucketName.contains(query, ignoreCase = true) ||
                visualAnalysisRepository.getLabels(item.id).any { it.contains(query, ignoreCase = true) } ||
                visualAnalysisRepository.getText(item.id).contains(query, ignoreCase = true)
            }
            val grouped = repository.groupByDate(results)

            val albumResults = (_uiState.value.mainAlbums + _uiState.value.otherAlbums).filter { album ->
                album.name.contains(query, ignoreCase = true)
            }

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(searchResults = grouped, albumSearchResults = albumResults)
            }
        }
    }

    fun openMoments() { _uiState.value = _uiState.value.copy(isMomentsOpen = true) }
    fun closeMoments() { _uiState.value = _uiState.value.copy(isMomentsOpen = false) }

    // PERF: Operação em background para não bloquear a Main thread
    private fun updateSearchSuggestionsAndPeople() {
        viewModelScope.launch(Dispatchers.Default) {
            val allMedia = _uiState.value.mediaGroups.flatMap { it.items }
            val allLabels = mutableSetOf<String>()
            val peopleAndPets = mutableListOf<MediaItem>()
            val mediaLabelsMap = mutableMapOf<Long, List<String>>()

            allMedia.forEach { item ->
                val labels = visualAnalysisRepository.getLabels(item.id)
                if (labels.isNotEmpty()) {
                    allLabels.addAll(labels)
                    mediaLabelsMap[item.id] = labels
                }
                if (visualAnalysisRepository.getFaceCount(item.id) > 0 ||
                    visualAnalysisRepository.hasPets(item.id)) {
                    peopleAndPets.add(item)
                }
            }

            val people = faceRepository.getAllPeople()

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    searchSuggestions = allLabels.toList().take(15),
                    peopleAndPets = peopleAndPets.take(20),
                    peopleWithFaces = people,
                    mediaLabels = mediaLabelsMap
                )
            }
        }
    }

    fun renamePerson(personId: String, newName: String) {
        faceRepository.renamePerson(personId, newName)
        _uiState.value = _uiState.value.copy(peopleWithFaces = faceRepository.getAllPeople())
    }

    fun mergePeople(targetId: String, sourceIds: List<String>) {
        faceRepository.mergePeople(targetId, sourceIds)
        _uiState.value = _uiState.value.copy(peopleWithFaces = faceRepository.getAllPeople())
    }

    fun optimizePeople() {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.value = _uiState.value.copy(isOptimizing = true, optimizationStatus = "Iniciando...")
            faceRepository.autoMergePeople { status ->
                _uiState.value = _uiState.value.copy(optimizationStatus = status)
            }
            val people = faceRepository.getAllPeople()
            _uiState.value = _uiState.value.copy(isOptimizing = false, peopleWithFaces = people)
            kotlinx.coroutines.delay(3000)
            _uiState.value = _uiState.value.copy(optimizationStatus = "")
        }
    }

    fun deletePerson(personId: String) {
        faceRepository.deletePerson(personId)
        updateSearchSuggestionsAndPeople()
    }

    fun searchFacesForPerson(personId: String) { optimizePeople() }

    fun removeFace(personId: String, faceId: String) {
        faceRepository.removeFaceFromPerson(personId, faceId)
        updateSearchSuggestionsAndPeople()
    }

    fun startManualFaceSelection() {
        _uiState.value = _uiState.value.copy(isManualFaceSelectionMode = true)
    }

    fun cancelManualFaceSelection() {
        _uiState.value = _uiState.value.copy(isManualFaceSelectionMode = false)
    }

    fun updateFaceBounds(faceId: String, newBounds: Rect) {
        faceRepository.updateFaceBounds(faceId, newBounds)
        updateSearchSuggestionsAndPeople()
        _uiState.value = _uiState.value.copy(isManualFaceSelectionMode = false)
    }

    fun loadMedia() {
        // BUG FIX: Invalida cache para forçar releitura fresca do MediaStore
        repository.invalidateCache()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val albumsData = withContext(Dispatchers.IO) {
                    val allAlbums = repository.getAlbums()
                    val favorites = repository.getFavorites()

                    // BUG FIX: Agora confia estritamente no status 'isPinned' definido no repositório
                    // Isso evita que pastas com nomes similares (ex: Downloads) vazem para a tela principal
                    val mainAlbums = allAlbums.filter { it.isPinned }
                    val otherAlbums = allAlbums.filter { !it.isPinned && it.type != AlbumType.FAVORITES }

                    val favoriteAlbum = if (favorites.isNotEmpty()) {
                        Album(-1, "Favoritos", favorites.first().uri, favorites.size,
                            -1, AlbumType.FAVORITES)
                    } else null

                    Triple(mainAlbums, otherAlbums, favoriteAlbum)
                }

                _uiState.value = _uiState.value.copy(
                    mainAlbums = albumsData.first,
                    otherAlbums = albumsData.second,
                    favoriteAlbum = albumsData.third,
                    userVirtualAlbums = repository.getUserVirtualAlbums()
                )

                withContext(Dispatchers.IO) {
                    val filter = when (_uiState.value.selectedFilter) {
                        MediaFilterChip.ALL -> MediaFilter.ALL
                        MediaFilterChip.CAMERA -> MediaFilter.CAMERA
                        MediaFilterChip.FAVORITES -> MediaFilter.FAVORITES
                        MediaFilterChip.VIDEOS -> MediaFilter.VIDEOS
                        MediaFilterChip.SCREENSHOTS -> MediaFilter.SCREENSHOTS
                    }

                    // Passo A: Primeiros 100 (instantâneo)
                    val initialMedia = repository.getAllMedia(filter, limit = 100)
                    val initialGroups = repository.groupByDate(initialMedia)
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            mediaGroups = initialGroups,
                            isLoading = false
                        )
                    }

                    // Passo B: Tudo em background
                    val allMedia = repository.getAllMedia(filter)
                    val allGroups = repository.groupByDate(allMedia)
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(mediaGroups = allGroups)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar mídia", e)
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun toggleDarkMode() {
        val newState = !_uiState.value.isDarkMode
        prefs.edit().putBoolean("dark_mode", newState).apply()
        _uiState.value = _uiState.value.copy(isDarkMode = newState)
    }

    fun toggleWhatsAppParser() {
        val newState = !_uiState.value.isWhatsAppParserEnabled
        prefs.edit().putBoolean("whatsapp_parser_enabled", newState).apply()
        _uiState.value = _uiState.value.copy(isWhatsAppParserEnabled = newState)
        if (newState) repository.loadWhatsAppMapping()
        loadMedia()
    }

    fun toggleSaveExif() {
        val newState = !_uiState.value.saveExifData
        prefs.edit().putBoolean("save_exif_data", newState).apply()
        _uiState.value = _uiState.value.copy(saveExifData = newState)
    }

    fun toggleFaceRecognition() {
        val newState = !_uiState.value.isFaceRecognitionEnabled
        prefs.edit().putBoolean("face_recognition_enabled", newState).apply()
        _uiState.value = _uiState.value.copy(isFaceRecognitionEnabled = newState)
    }

    fun importWhatsAppBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
                } ?: "backup.txt"

                _uiState.value = _uiState.value.copy(whatsappBackupName = fileName, importProgress = 0.1f)

                // PERF: Leitura do arquivo em IO thread
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        it.bufferedReader().readText()
                    } ?: ""
                }

                _uiState.value = _uiState.value.copy(importProgress = 0.4f)

                val mappingList = withContext(Dispatchers.Default) {
                    com.hypergallery.util.WhatsAppParser.parseWhatsAppBackup(content)
                }

                _uiState.value = _uiState.value.copy(importProgress = 0.7f)

                withContext(Dispatchers.IO) {
                    repository.setWhatsAppMapping(mappingList, fileName)
                }

                _uiState.value = _uiState.value.copy(
                    isWhatsAppParserEnabled = true, importProgress = 1.0f
                )

                loadMedia()
                kotlinx.coroutines.delay(1000)
                _uiState.value = _uiState.value.copy(importProgress = null)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao importar backup WhatsApp", e)
                _uiState.value = _uiState.value.copy(
                    error = "Erro ao importar backup: ${e.message}",
                    importProgress = null
                )
            }
        }
    }

    fun openSettings() { _uiState.value = _uiState.value.copy(isSettingsOpen = true) }
    fun closeSettings() { _uiState.value = _uiState.value.copy(isSettingsOpen = false) }
    fun toggleShowAllAlbums() { _uiState.value = _uiState.value.copy(showAllAlbums = !_uiState.value.showAllAlbums) }

    fun selectAlbum(album: Album) {
        val isGroupingFolder = album.isVirtualFolder &&
            (album.bucketId == -3L || album.bucketId == -888L || album.bucketId == -2L ||
             album.bucketId == -4L || album.bucketId == -5L || album.bucketId == -6L ||
             album.bucketId < -10L) // Inclui novos IDs de roots virtuais

        if (isGroupingFolder) {
            viewModelScope.launch {
                loadSubAlbums(album.relativePath, album.name)
            }
            return
        }
        
        loadAlbumContent(album)
    }

    private suspend fun loadSubAlbums(path: String, title: String) {
        Log.d(TAG, "Opening grouping folder: $title ($path)")
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            currentFolderPath = path,
            currentFolderTitle = title
        )
        val subAlbums = withContext(Dispatchers.IO) {
            repository.getAlbums(path, sort = _uiState.value.albumSort)
        }
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            mainAlbums = subAlbums.filter { it.type != AlbumType.FOLDER && it.type != AlbumType.FAVORITES },
            otherAlbums = subAlbums.filter { it.type == AlbumType.FOLDER },
            favoriteAlbum = null
        )
    }

    private fun loadAlbumContent(album: Album) {
        viewModelScope.launch {
            val newScrollPos = if (_uiState.value.selectedAlbum?.id != album.id) 0
                              else _uiState.value.albumScrollPosition
            _uiState.value = _uiState.value.copy(
                selectedAlbum = album,
                isAlbumLoading = true,
                albumMedia = emptyList(),
                albumScrollPosition = newScrollPos
            )
            try {
                val mediaItems = withContext(Dispatchers.IO) {
                    when {
                        album.id == -1L -> repository.getFavorites()
                        album.id == -999L || album.bucketId == -999L -> repository.getBackupMedia()
                        album.relativePath.startsWith("whatsapp/contacts/") ->
                            repository.getMediaByContact(album.name)
                        else -> repository.getMediaByBucket(album.bucketId)
                    }
                }
                val media = mediaItems.map { it.copy(text = visualAnalysisRepository.getText(it.id)) }
                val groups = withContext(Dispatchers.Default) { repository.groupByDate(media) }
                _uiState.value = _uiState.value.copy(isAlbumLoading = false, albumMedia = groups)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading album", e)
                _uiState.value = _uiState.value.copy(isAlbumLoading = false)
            }
        }
    }

    fun clearSelectedAlbum() {
        val currentScrollPos = _uiState.value.albumScrollPosition
        if (_uiState.value.selectedAlbum == null && _uiState.value.currentFolderPath != null) {
            _uiState.value = _uiState.value.copy(currentFolderPath = null, currentFolderTitle = null)
            loadMedia()
            return
        }
        _uiState.value = _uiState.value.copy(
            selectedAlbum = null,
            albumMedia = emptyList(),
            albumScrollPosition = currentScrollPos
        )
    }

    fun renameAlbum(bucketId: Long, newName: String) { repository.renameAlbum(bucketId, newName); loadMedia() }
    fun createAlbum(name: String) { repository.createAlbum(name, _uiState.value.currentFolderPath); loadMedia() }
    fun setAlbumCover(bucketId: Long, uri: android.net.Uri) {
        repository.setAlbumCover(bucketId, uri)
        _uiState.value = _uiState.value.copy(selectingCoverForAlbumId = null)
        loadMedia()
    }

    fun startSelectingCover(bucketId: Long) {
        val album = (_uiState.value.mainAlbums + _uiState.value.otherAlbums).find { it.bucketId == bucketId }
        album?.let {
            _uiState.value = _uiState.value.copy(selectingCoverForAlbumId = bucketId)
            selectAlbum(it)
        }
    }

    fun cancelSelectingCover() { _uiState.value = _uiState.value.copy(selectingCoverForAlbumId = null) }

    fun toggleAlbumAnalysis(bucketId: Long, enabled: Boolean) {
        repository.setAlbumAnalysisEnabled(bucketId, enabled)
        loadMedia()
        startBackgroundAnalysis()
    }

    fun toggleAlbumPinned(bucketId: Long, pinned: Boolean) {
        repository.setAlbumPinned(bucketId, pinned)
        loadMedia()
    }

    fun addAlbumToVirtual(virtualName: String, bucketId: Long) { repository.addAlbumToVirtual(virtualName, bucketId); loadMedia() }
    fun createVirtualAlbum(name: String, bucketId: Long) { repository.addAlbumToVirtual(name, bucketId); loadMedia() }
    fun removeAlbumFromVirtual(virtualName: String, bucketId: Long) { repository.removeAlbumFromVirtual(virtualName, bucketId); loadMedia() }
    fun deleteVirtualAlbum(name: String) { repository.deleteVirtualAlbum(name); loadMedia() }
    fun createVirtualAlbumFromFolder(name: String, path: String) { repository.addVirtualFolderRoot(name, path); loadMedia() }
    fun deleteVirtualFolderRoot(name: String) { repository.deleteVirtualFolderRoot(name); loadMedia() }
    fun setPendingFolder(path: String) { _uiState.value = _uiState.value.copy(pendingFolderPath = path) }
    fun clearPendingFolder() { _uiState.value = _uiState.value.copy(pendingFolderPath = null) }

    fun openViewer(mediaList: List<MediaItem>, index: Int = 0, useLastPosition: Boolean = true, fromPerson: Boolean = false) {
        val startIndex = if (useLastPosition && index == 0 && _uiState.value.lastViewerIndex > 0) {
            _uiState.value.lastViewerIndex.coerceAtMost(mediaList.size - 1)
        } else index

        // PERF: Enriquecimento em background
        viewModelScope.launch(Dispatchers.Default) {
            val enrichedList = mediaList.map { it.copy(text = visualAnalysisRepository.getText(it.id)) }
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    viewerMedia = enrichedList,
                    viewerStartIndex = startIndex,
                    isViewerOpen = true,
                    isViewingFromPerson = fromPerson,
                    lastViewerIndex = -1
                )
            }
        }
    }

    fun openViewerWithScroll(mediaList: List<MediaItem>, startIndex: Int, scrollPosition: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            val enrichedList = mediaList.map { it.copy(text = visualAnalysisRepository.getText(it.id)) }
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    viewerMedia = enrichedList,
                    viewerStartIndex = startIndex,
                    albumScrollPosition = scrollPosition,
                    isViewerOpen = true,
                    isViewingFromPerson = false,
                    lastViewerIndex = -1
                )
            }
        }
    }

    fun closeViewer(currentIndex: Int = 0) {
        _uiState.value = _uiState.value.copy(
            isViewerOpen = false,
            lastViewerIndex = currentIndex,
            currentMediaFaces = emptyList()
        )
    }

    fun getFacesForMedia(mediaId: Long) {
        _uiState.value = _uiState.value.copy(currentMediaFaces = faceRepository.getFacesForMedia(mediaId))
    }

    fun goToMediaInAlbum(mediaItem: MediaItem) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isViewerOpen = false, isDetailsOpen = false)
            setCurrentTab(GalleryTab.ALBUMS)
            kotlinx.coroutines.delay(50)

            // Usa o banco de dados para encontrar o caminho do álbum sem precisar recarregar tudo do MediaStore
            val albumPath = withContext(Dispatchers.IO) { repository.getAlbumPathById(mediaItem.bucketId) }
            
            if (albumPath != null) {
                // Tenta resolver qual é a raiz (se houver) para este caminho para abrir a visualização correta
                val roots = repository.getVirtualFolderRoots()
                val activeRoot = roots.entries.find { albumPath.startsWith(it.value) && albumPath != it.value }
                
                if (activeRoot != null) {
                    loadSubAlbums(activeRoot.value, activeRoot.key)
                } else {
                    val virtualAlbums = repository.getUserVirtualAlbums()
                    val activeVirtual = virtualAlbums.entries.find { it.value.contains(mediaItem.bucketId) }
                    if (activeVirtual != null) {
                        loadSubAlbums("virtual/user/${activeVirtual.key}", activeVirtual.key)
                    } else if (albumPath.contains("WhatsApp", ignoreCase = true)) {
                        loadSubAlbums("virtual/whatsapp", "WhatsApp")
                    } else if (albumPath.lowercase().contains("screenshots") || albumPath.lowercase().contains("screenrecorder")) {
                        loadSubAlbums("virtual/recordings", "Gravações")
                    } else if (albumPath.lowercase().contains("/pictures/gallery/owner")) {
                        loadSubAlbums("virtual/galeria", "Galeria")
                    } else {
                        // Está na raiz, recarrega a tela principal de álbuns
                        loadMedia()
                    }
                }
            }

            val allBuckets = withContext(Dispatchers.IO) { repository.getAlbums(_uiState.value.currentFolderPath) }
            val album = allBuckets.find { it.bucketId == mediaItem.bucketId }

            if (album != null) {
                loadAlbumContent(album)
                // Aguarda carregamento com timeout para evitar loop infinito
                var waited = 0
                while (_uiState.value.isAlbumLoading && waited < 5000) {
                    kotlinx.coroutines.delay(100)
                    waited += 100
                }

                var targetIndex = 0
                var found = false
                for (group in _uiState.value.albumMedia) {
                    if (group.items.any { it.id == mediaItem.id }) {
                        targetIndex += group.items.indexOfFirst { it.id == mediaItem.id } + 1
                        found = true
                        break
                    }
                    targetIndex += group.items.size + 1
                }
                if (found) _uiState.value = _uiState.value.copy(albumScrollPosition = targetIndex)
            } else {
                Log.w(TAG, "Album ID ${mediaItem.bucketId} not found. Returning to Photos.")
                _uiState.value = _uiState.value.copy(selectedAlbum = null, currentTab = GalleryTab.PHOTOS)
            }
        }
    }

    fun openDetails() { _uiState.value = _uiState.value.copy(isDetailsOpen = true) }
    fun closeDetails() { _uiState.value = _uiState.value.copy(isDetailsOpen = false) }
    fun saveScrollPosition(position: Int) { _uiState.value = _uiState.value.copy(albumScrollPosition = position) }

    fun toggleSelection(mediaId: Long) {
        val current = _uiState.value.selectedItems.toMutableSet()
        if (!current.add(mediaId)) current.remove(mediaId)
        _uiState.value = _uiState.value.copy(
            selectedItems = current,
            isSelectionMode = current.isNotEmpty()
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedItems = emptySet(), isSelectionMode = false)
    }

    fun deleteSelectedMedia() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value.selectedItems.forEach { repository.moveToTrash(it) }
            withContext(Dispatchers.Main) { clearSelection() }
        }
    }

    fun deleteSingleMedia(mediaItem: MediaItem) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.moveToTrash(mediaItem.id)
            val updatedList = _uiState.value.viewerMedia.filter { it.id != mediaItem.id }
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(viewerMedia = updatedList)
            }
        }
    }

    fun toggleFavorite(mediaItem: MediaItem) {
        // TODO: Implementar toggle de favorito via MediaStore API
        Log.d(TAG, "Toggle favorite: ${mediaItem.id}")
    }

    fun openTrash() {
        viewModelScope.launch {
            val trashMedia = withContext(Dispatchers.IO) { repository.getTrashMedia() }
            if (trashMedia.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isViewerOpen = true,
                    viewerMedia = trashMedia,
                    viewerStartIndex = 0,
                    selectedAlbum = Album(
                        id = -100, name = "Lixeira",
                        coverUri = android.net.Uri.EMPTY,
                        itemCount = trashMedia.size, bucketId = -100, type = AlbumType.FOLDER
                    )
                )
            }
        }
    }

    fun restoreFromTrash(mediaId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.restoreFromTrash(mediaId)
            val trashMedia = repository.getTrashMedia()
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    viewerMedia = trashMedia,
                    selectedAlbum = _uiState.value.selectedAlbum?.copy(itemCount = trashMedia.size)
                )
            }
        }
    }

    fun permanentlyDelete(mediaId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePermanently(mediaId)
            repository.restoreFromTrash(mediaId)
            val trashMedia = repository.getTrashMedia()
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    viewerMedia = trashMedia,
                    selectedAlbum = _uiState.value.selectedAlbum?.copy(itemCount = trashMedia.size)
                )
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.emptyTrash()
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(isViewerOpen = false, viewerMedia = emptyList())
            }
        }
    }

    fun setFilter(filter: MediaFilterChip) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
        loadMedia()
    }

    fun setCurrentTab(tab: GalleryTab) { _uiState.value = _uiState.value.copy(currentTab = tab) }

    fun setAlbumSort(sort: com.hypergallery.data.AlbumSort) {
        _uiState.value = _uiState.value.copy(albumSort = sort)
        loadMedia()
    }

    fun extractTextFromMedia(mediaItem: MediaItem, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val savedText = visualAnalysisRepository.getText(mediaItem.id)
            if (savedText.isNotBlank()) {
                onResult(savedText)
            } else {
                val result = withContext(Dispatchers.IO) {
                    visualAnalysisRepository.analyzeImage(mediaItem.uri, mediaItem.id)
                }
                onResult(result.text)
            }
        }
    }

    fun renameMedia(mediaItem: MediaItem, newName: String) {
        // TODO: Implementar renomear via MediaStore (requer permissão de escrita)
        Log.d(TAG, "Renaming ${mediaItem.name} to $newName")
    }

    fun getMetadata(mediaId: Long): ImageMetadata = metadataRepository.getMetadata(mediaId)

    fun saveMetadata(mediaId: Long, description: String, tags: List<String>) {
        metadataRepository.saveMetadata(mediaId, description, tags)
    }

    fun readExifFromUri(uri: android.net.Uri): com.hypergallery.data.ExifData? =
        metadataRepository.readExifFromUri(getApplication(), uri)

    fun getExifData(uri: android.net.Uri): com.hypergallery.data.ExifData? =
        try { metadataRepository.readExifFromUri(getApplication(), uri) } catch (e: Exception) { null }
}
