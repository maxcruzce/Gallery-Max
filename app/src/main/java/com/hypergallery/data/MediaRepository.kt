package com.hypergallery.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.hypergallery.util.MediaMap
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*

class MediaRepository(private val context: Context) {

    companion object {
        private const val TAG = "MediaRepository"
        private const val TRASH_KEY = "trash_media_ids"
        private const val WHATSAPP_MAPPING_KEY = "whatsapp_mapping"
        private const val WHATSAPP_DATE_MAPPING_KEY = "whatsapp_date_mapping"
        private const val WHATSAPP_TIMESTAMP_MAPPING_KEY = "whatsapp_timestamp_mapping"
        private const val WHATSAPP_BACKUP_LIST_KEY = "whatsapp_backup_list"
    }

    private val contentResolver: ContentResolver get() = context.contentResolver

    private val imageUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    private val videoUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    private val albumPathMap = mutableMapOf<Long, String>()
    private val whatsappMapping = mutableMapOf<String, String>()
    private val whatsappDateMapping = mutableMapOf<String, String>()
    private val whatsappTimestampMapping = mutableMapOf<Long, String>()
    private val backupMediaList = mutableListOf<MediaMap>()

    // BUG FIX: Cache por filtro com tamanho limitado para evitar crescimento ilimitado de memória
    private val mediaCache = mutableMapOf<MediaFilter, List<MediaItem>>()

    private val prefs = context.getSharedPreferences("hypergallery_prefs", Context.MODE_PRIVATE)

    init { loadWhatsAppMapping() }

    fun loadWhatsAppMapping() {
        if (!prefs.getBoolean("whatsapp_parser_enabled", false)) return

        // BUG FIX: WhatsApp mapping com tratamento robusto de erros em cada entrada
        prefs.getString(WHATSAPP_MAPPING_KEY, null)?.split("|")?.forEach {
            val parts = it.split(":", limit = 2) // limit=2 para suportar ":" no nome do sender
            if (parts.size == 2) whatsappMapping[parts[0]] = parts[1]
        }
        prefs.getString(WHATSAPP_DATE_MAPPING_KEY, null)?.split("|")?.forEach {
            val parts = it.split(":", limit = 2)
            if (parts.size == 2) whatsappDateMapping[parts[0]] = parts[1]
        }
        prefs.getString(WHATSAPP_TIMESTAMP_MAPPING_KEY, null)?.split("|")?.forEach {
            val parts = it.split(":", limit = 2)
            if (parts.size == 2) {
                parts[0].toLongOrNull()?.let { ts -> whatsappTimestampMapping[ts] = parts[1] }
            }
        }
        prefs.getString(WHATSAPP_BACKUP_LIST_KEY, null)?.split("|||")?.forEach { entry ->
            val parts = entry.split(":::")
            if (parts.size >= 3) {
                backupMediaList.add(
                    MediaMap(
                        parts[0], parts[1], parts[2],
                        if (parts.size > 3) parts[3].toLongOrNull() ?: 0L else 0L,
                        if (parts.size > 4) parts[4] == "true" else false
                    )
                )
            }
        }
    }

    fun clearWhatsAppMapping() {
        whatsappMapping.clear()
        whatsappDateMapping.clear()
        whatsappTimestampMapping.clear()
        backupMediaList.clear()
        prefs.edit()
            .remove(WHATSAPP_MAPPING_KEY)
            .remove(WHATSAPP_DATE_MAPPING_KEY)
            .remove(WHATSAPP_TIMESTAMP_MAPPING_KEY)
            .remove(WHATSAPP_BACKUP_LIST_KEY)
            .remove("whatsapp_backup_name")
            .remove("whatsapp_media_count")
            .apply()
    }

    private fun saveWhatsAppMapping(backupName: String? = null) {
        val stringMapping = whatsappMapping.entries.joinToString("|") { "${it.key}:${it.value}" }
        val stringDateMapping = whatsappDateMapping.entries.joinToString("|") { "${it.key}:${it.value}" }
        val stringTimestampMapping = whatsappTimestampMapping.entries.joinToString("|") { "${it.key}:${it.value}" }
        val stringBackupList = backupMediaList.joinToString("|||") {
            "${it.fileName}:::${it.sender}:::${it.timestamp}:::${it.dateAddedMs}:::${it.isHiddenMedia}"
        }
        prefs.edit()
            .putString(WHATSAPP_MAPPING_KEY, stringMapping)
            .putString(WHATSAPP_DATE_MAPPING_KEY, stringDateMapping)
            .putString(WHATSAPP_TIMESTAMP_MAPPING_KEY, stringTimestampMapping)
            .putString(WHATSAPP_BACKUP_LIST_KEY, stringBackupList)
            .also { editor -> backupName?.let { editor.putString("whatsapp_backup_name", it) } }
            .apply()
    }

    fun setWhatsAppMapping(mapping: List<MediaMap>, backupName: String? = null) {
        whatsappMapping.clear()
        whatsappDateMapping.clear()
        backupMediaList.clear()
        mapping.forEach { m ->
            backupMediaList.add(m)
            if (m.fileName != "Mídia oculta") whatsappMapping[m.fileName] = m.sender
            whatsappDateMapping[m.timestamp] = m.sender
            if (m.dateAddedMs > 0) whatsappTimestampMapping[m.dateAddedMs] = m.sender
        }
        saveWhatsAppMapping(backupName)
    }

    suspend fun getBackupMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        if (backupMediaList.isEmpty()) return@withContext emptyList()
        val items = mutableListOf<MediaItem>()
        items.addAll(queryMediaWithSelection(imageUri, null, null))
        items.addAll(queryMediaWithSelection(videoUri, null, null))
        items.filter { it.bucketName != "Outros" && it.bucketName != it.name }
            .sortedByDescending { it.dateAdded }
    }

    suspend fun getAllMedia(filter: MediaFilter = MediaFilter.ALL, limit: Int = -1): List<MediaItem> =
        withContext(Dispatchers.IO) {
            // BUG FIX: Cache só retorna se tivermos dados completos (limit == -1)
            if (limit == -1 && mediaCache.containsKey(filter)) {
                return@withContext mediaCache[filter]!!
            }
            if (limit > 0 && mediaCache.containsKey(filter)) {
                return@withContext mediaCache[filter]!!.take(limit)
            }

            val items = mutableListOf<MediaItem>()
            val selectionPair: Pair<String?, Array<String>?> = when (filter) {
                MediaFilter.FAVORITES ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        "${MediaStore.Images.Media.IS_FAVORITE} = ?" to arrayOf("1")
                    else null to null
                MediaFilter.SCREENSHOTS ->
                    "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ?" to arrayOf("%Screenshot%")
                MediaFilter.CAMERA ->
                    ("${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ? OR " +
                     "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ? OR " +
                     "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?") to
                    arrayOf("%Camera%", "%DCIM%", "100ANDRO")
                else -> null to null
            }

            coroutineScope {
                val img = if (filter != MediaFilter.VIDEOS)
                    async { queryMediaWithSelection(imageUri, selectionPair.first, selectionPair.second, limit) }
                else null
                val vid = if (filter != MediaFilter.PHOTOS)
                    async { queryMediaWithSelection(videoUri, selectionPair.first, selectionPair.second, limit) }
                else null
                img?.await()?.let { items.addAll(it) }
                vid?.await()?.let { items.addAll(it) }
            }

            val sorted = items.sortedByDescending { it.dateAdded }
            // BUG FIX: Só guarda cache completo para evitar retornar lista parcial
            if (limit == -1) mediaCache[filter] = sorted
            if (limit > 0 && sorted.size > limit) sorted.take(limit) else sorted
        }

    // BUG FIX: Cache invalidado quando necessário (ex: após delete ou reload)
    fun invalidateCache() {
        mediaCache.clear()
    }

    private fun queryMediaWithSelection(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?,
        limit: Int = -1
    ): List<MediaItem> {
        val isVideo = uri.toString().contains("video")

        val baseProjection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT
        )

        val projection = when {
            isVideo -> baseProjection + arrayOf(MediaStore.Video.Media.DURATION)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                baseProjection + arrayOf(MediaStore.Images.Media.IS_FAVORITE)
            else -> baseProjection
        }

        val items = mutableListOf<MediaItem>()
        // BUG FIX: LIMIT injetado na sortOrder funciona apenas em alguns providers.
        // A forma correta é usar Bundle query para Android 10+ ou limitar na iteração.
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        try {
            val queryUri = if (limit > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Usa LIMIT via ContentResolver Bundle no Android 8+
                uri
            } else uri

            val cursor = if (limit > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val queryArgs = android.os.Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                    putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                    putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                }
                // BUG FIX: Bundle query disponível apenas API 26+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    contentResolver.query(queryUri, projection, queryArgs, null)
                } else {
                    contentResolver.query(queryUri, projection, selection, selectionArgs, sortOrder)
                }
            } else {
                contentResolver.query(queryUri, projection, selection, selectionArgs, sortOrder)
            }

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val bNmCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val bIdCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                val dtTCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val dtACol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val szCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val miCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val wCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val hCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val duCol = if (isVideo) c.getColumnIndex(MediaStore.Video.Media.DURATION) else -1
                val fCol = if (!isVideo && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    c.getColumnIndex(MediaStore.Images.Media.IS_FAVORITE) else -1

                var count = 0
                while (c.moveToNext()) {
                    // BUG FIX: Respeitar limit via contagem ao invés de LIMIT na query (compatibilidade)
                    if (limit > 0 && count >= limit) break

                    val fn = c.getString(nameCol) ?: ""
                    var da = c.getLong(dtACol) * 1000L
                    var dt = c.getLong(dtTCol)
                    extractDateFromFileName(fn)?.let {
                        if (dt <= 0) dt = it
                        if (da <= 0 || da < 946684800000L) da = it
                    }
                    val bn = mapToSender(fn, c.getString(bNmCol) ?: "Outros", da)
                    val id = c.getLong(idCol)

                    items.add(
                        MediaItem(
                            id = id,
                            uri = ContentUris.withAppendedId(uri, id),
                            name = fn,
                            bucketName = bn,
                            bucketId = c.getLong(bIdCol),
                            dateTaken = dt,
                            dateAdded = da,
                            size = c.getLong(szCol),
                            mimeType = c.getString(miCol) ?: if (isVideo) "video/mp4" else "image/jpeg",
                            width = c.getInt(wCol),
                            height = c.getInt(hCol),
                            duration = if (duCol != -1) c.getLong(duCol) else 0L,
                            isFavorite = fCol != -1 && c.getInt(fCol) == 1
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Query error on $uri", e)
        }
        return items
    }

    private fun extractDateFromFileName(fn: String): Long? {
        val patterns = listOf(
            Regex("""(\d{8})_(\d{6})"""),
            Regex("""(\d{4})-(\d{2})-(\d{2})"""),
            Regex("""Screenshot_(\d{4})(\d{2})(\d{2})"""),
            Regex("""IMG_(\d{8})_(\d{6})"""),
            Regex("""(\d{8})""")
        )
        for (r in patterns) {
            val m = r.find(fn) ?: continue
            try {
                val cal = Calendar.getInstance()
                val g = m.groupValues
                when {
                    r.pattern.contains("""_(\d{6})""") -> {
                        val ymd = g[1].takeLast(8)
                        val hms = g[2]
                        cal.set(
                            ymd.substring(0, 4).toInt(),
                            ymd.substring(4, 6).toInt() - 1,
                            ymd.substring(6, 8).toInt(),
                            hms.substring(0, 2).toInt(),
                            hms.substring(2, 4).toInt(),
                            hms.substring(4, 6).toInt()
                        )
                    }
                    g.size >= 4 -> cal.set(g[1].toInt(), g[2].toInt() - 1, g[3].toInt(), 12, 0)
                    else -> {
                        val s = g[1]
                        if (s.length < 8) continue
                        cal.set(
                            s.substring(0, 4).toInt(),
                            s.substring(4, 6).toInt() - 1,
                            s.substring(6, 8).toInt(), 12, 0
                        )
                    }
                }
                val ts = cal.timeInMillis
                // BUG FIX: Janela de data válida (2000-01-01 até 1 ano no futuro)
                if (ts > 946684800000L && ts < System.currentTimeMillis() + 31536000000L) return ts
            } catch (_: Exception) {}
        }
        return null
    }

    private fun mapToSender(fn: String, bdn: String, da: Long): String {
        val tolerance = 30_000L
        backupMediaList.find { it.dateAddedMs > 0 && kotlin.math.abs(it.dateAddedMs - da) <= tolerance }
            ?.let { return it.sender }
        whatsappMapping[fn]?.let { return it }
        return bdn
    }

    fun getSavedBackupName(): String? = prefs.getString("whatsapp_backup_name", null)
    fun hasWhatsAppBackup(): Boolean = prefs.getString("whatsapp_backup_name", null) != null
    fun setAlbumAnalysisEnabled(id: Long, enabled: Boolean) {
        prefs.edit().putBoolean("album_analysis_$id", enabled).apply()
    }

    suspend fun getMediaByContact(name: String): List<MediaItem> =
        withContext(Dispatchers.IO) { getBackupMedia().filter { it.bucketName == name } }

    private fun isCameraBucket(id: Long): Boolean = id == -1739773001L || id == 540528482L

    fun getVirtualFolderRoots(): Map<String, String> {
        val json = prefs.getString("virtual_folder_roots", "{}") ?: "{}"
        val type = object : TypeToken<Map<String, String>>() {}.type
        return try { Gson().fromJson(json, type) ?: emptyMap() } catch (e: Exception) { emptyMap() }
    }

    fun addVirtualFolderRoot(name: String, path: String) {
        val roots = getVirtualFolderRoots().toMutableMap()
        roots[name] = path
        prefs.edit().putString("virtual_folder_roots", Gson().toJson(roots)).apply()
    }

    fun deleteVirtualFolderRoot(name: String) {
        val roots = getVirtualFolderRoots().toMutableMap()
        roots.remove(name)
        prefs.edit().putString("virtual_folder_roots", Gson().toJson(roots)).apply()
    }

    fun renameAlbum(bucketId: Long, newName: String) {
        prefs.edit().putString("album_name_$bucketId", newName).apply()
    }

    fun createAlbum(name: String, parentPath: String? = null): Boolean {
        val root = if (parentPath == null || parentPath == "virtual/galeria")
            "/storage/emulated/0/Pictures/Gallery/owner" else parentPath
        val folder = java.io.File(root, name)
        return if (!folder.exists()) folder.mkdirs() else true
    }

    fun setAlbumCover(bucketId: Long, uri: Uri) {
        prefs.edit().putString("album_cover_$bucketId", uri.toString()).apply()
    }

    fun setAlbumPinned(bucketId: Long, pinned: Boolean) {
        prefs.edit().putBoolean("album_pinned_$bucketId", pinned).apply()
    }

    fun hasExplicitPinnedPreference(bucketId: Long): Boolean = prefs.contains("album_pinned_$bucketId")

    fun getUserVirtualAlbums(): Map<String, List<Long>> {
        val json = prefs.getString("user_virtual_albums", "{}") ?: "{}"
        val type = object : TypeToken<Map<String, List<Long>>>() {}.type
        return try { Gson().fromJson(json, type) ?: emptyMap() } catch (e: Exception) { emptyMap() }
    }

    fun addAlbumToVirtual(name: String, bucketId: Long) {
        val map = getUserVirtualAlbums().toMutableMap()
        val list = map[name]?.toMutableList() ?: mutableListOf()
        if (bucketId !in list) {
            list.add(bucketId)
            map[name] = list
            prefs.edit().putString("user_virtual_albums", Gson().toJson(map)).apply()
        }
    }

    fun removeAlbumFromVirtual(name: String, bucketId: Long) {
        val map = getUserVirtualAlbums().toMutableMap()
        map[name]?.toMutableList()?.let {
            if (it.remove(bucketId)) {
                map[name] = it
                prefs.edit().putString("user_virtual_albums", Gson().toJson(map)).apply()
            }
        }
    }

    fun deleteVirtualAlbum(name: String) {
        val map = getUserVirtualAlbums().toMutableMap()
        map.remove(name)
        prefs.edit().putString("user_virtual_albums", Gson().toJson(map)).apply()
    }

    suspend fun getTrashMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        val savedTrash = prefs.getString(TRASH_KEY, "") ?: ""
        if (savedTrash.isEmpty()) return@withContext emptyList()
        val ids = savedTrash.split(",").mapNotNull { it.trim().toLongOrNull() }
        if (ids.isEmpty()) return@withContext emptyList()

        val items = mutableListOf<MediaItem>()
        val placeholders = ids.joinToString(",") { "?" }
        val selection = "${MediaStore.Images.Media._ID} IN ($placeholders)"
        val args = ids.map { it.toString() }.toTypedArray()

        items.addAll(queryMediaWithSelection(imageUri, selection, args))
        items.addAll(queryMediaWithSelection(videoUri, selection, args))
        items.sortedByDescending { it.dateAdded }
    }

    fun moveToTrash(id: Long): Boolean {
        val current = prefs.getString(TRASH_KEY, "") ?: ""
        val list = current.split(",").filter { it.isNotEmpty() }.toMutableList()
        if (id.toString() !in list) {
            list.add(id.toString())
            prefs.edit().putString(TRASH_KEY, list.joinToString(",")).apply()
        }
        invalidateCache()
        return true
    }

    fun restoreFromTrash(id: Long): Boolean {
        val current = prefs.getString(TRASH_KEY, "") ?: ""
        val list = current.split(",").filter { it.isNotEmpty() }.toMutableList()
        if (list.remove(id.toString())) {
            prefs.edit().putString(TRASH_KEY, list.joinToString(",")).apply()
        }
        return true
    }

    fun deletePermanently(id: Long): Boolean {
        val deletedImage = contentResolver.delete(
            imageUri, "${MediaStore.MediaColumns._ID} = ?", arrayOf(id.toString())
        ) > 0
        val deletedVideo = if (!deletedImage) contentResolver.delete(
            videoUri, "${MediaStore.MediaColumns._ID} = ?", arrayOf(id.toString())
        ) > 0 else false
        if (deletedImage || deletedVideo) invalidateCache()
        return deletedImage || deletedVideo
    }

    fun emptyTrash(): Boolean {
        prefs.edit().remove(TRASH_KEY).apply()
        return true
    }

    suspend fun getAlbums(parentPath: String? = null, sort: AlbumSort = AlbumSort.DATE_DESC): List<Album> =
        withContext(Dispatchers.IO) {
            val bMap = mutableMapOf<Long, AlbumBuilder>()

            val imgProj = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_ADDED
            )

            contentResolver.query(imageUri, imgProj, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { c ->
                val idC = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val biC = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val bnC = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dC = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val daC = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (c.moveToNext()) {
                    val bi = c.getLong(biC)
                    val da = c.getLong(daC)
                    val path = c.getString(dC) ?: continue
                    val builder = bMap.getOrPut(bi) {
                        AlbumBuilder(bi, c.getString(bnC) ?: "Outros",
                            ContentUris.withAppendedId(imageUri, c.getLong(idC)),
                            path.substringBeforeLast("/"), 0, da)
                    }
                    if (da > builder.latestTimestamp) {
                        builder.latestTimestamp = da
                        builder.coverUri = ContentUris.withAppendedId(imageUri, c.getLong(idC))
                    }
                    builder.count++
                }
            }

            val vidProj = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.BUCKET_ID,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DATE_ADDED
            )
            contentResolver.query(videoUri, vidProj, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
                val idC = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val biC = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                val bnC = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val dC = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val daC = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                while (c.moveToNext()) {
                    val bi = c.getLong(biC)
                    val da = c.getLong(daC)
                    val path = c.getString(dC) ?: continue
                    val builder = bMap.getOrPut(bi) {
                        AlbumBuilder(bi, c.getString(bnC) ?: "Outros",
                            ContentUris.withAppendedId(videoUri, c.getLong(idC)),
                            path.substringBeforeLast("/"), 0, da)
                    }
                    if (da > builder.latestTimestamp) {
                        builder.latestTimestamp = da
                        builder.coverUri = ContentUris.withAppendedId(videoUri, c.getLong(idC))
                    }
                    builder.count++
                }
            }

            // Scanner de pastas ocultas WhatsApp
            val hiddenPaths = listOf(
                "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/Sent",
                "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/Private",
                "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video/Sent",
                "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video/Private"
            )
            hiddenPaths.forEach { p ->
                val f = java.io.File(p)
                if (f.exists() && f.isDirectory) {
                    val files = f.listFiles { fl ->
                        fl.extension.lowercase() in listOf("jpg", "jpeg", "png", "mp4", "gif")
                    }
                    if (!files.isNullOrEmpty()) {
                        val bid = p.hashCode().toLong()
                        val name = when {
                            p.contains("Images") && p.contains("Sent") -> "Fotos Enviadas"
                            p.contains("Images") -> "Fotos Privadas"
                            p.contains("Sent") -> "Vídeos Enviados"
                            else -> "Vídeos Privados"
                        }
                        val sortedFiles = files.sortedByDescending { it.lastModified() }
                        val album = Album(
                            bid, name, Uri.fromFile(sortedFiles.first()), files.size,
                            bid, AlbumType.WHATSAPP, p, false,
                            isAlbumPinned(bid), isAlbumAnalysisEnabled(bid),
                            null, null, sortedFiles.first().lastModified() / 1000, emptyList()
                        )
                        albumPathMap[bid] = p
                        bMap[bid] = AlbumBuilder(bid, name, album.coverUri, p, files.size, album.latestTimestamp)
                    }
                }
            }

            val allBuckets = bMap.values.map { it.toAlbum(this@MediaRepository) }.toMutableList()

            if (parentPath == null) {
                val finalAlbums = mutableListOf<Album>()
                val virtualAlbums = getUserVirtualAlbums()

                // 1. Câmera
                allBuckets.filter { it.type == AlbumType.CAMERA || isCameraBucket(it.bucketId) }.forEach {
                    val isPinned = if (hasExplicitPinnedPreference(it.id)) isAlbumPinned(it.id) else true
                    finalAlbums.add(it.copy(isPinned = isPinned))
                }

                // 2. Gravações
                val recId = "rec_root".hashCode().toLong()
                val recFolders = allBuckets.filter {
                    it.relativePath.lowercase().contains("dcim/screenshots") ||
                    it.relativePath.lowercase().contains("dcim/screenrecorder") ||
                    it.type == AlbumType.SCREENSHOTS
                }
                if (recFolders.isNotEmpty()) {
                    val isPinned = if (hasExplicitPinnedPreference(recId)) isAlbumPinned(recId) else true
                    finalAlbums.add(
                        Album(recId, "Gravações", recFolders.first().coverUri,
                            recFolders.sumOf { it.itemCount }, -6, AlbumType.FOLDER,
                            "virtual/recordings", true, isPinned, isAlbumAnalysisEnabled(recId),
                            null, null, recFolders.maxOf { it.latestTimestamp },
                            recFolders.take(4).map { it.coverUri })
                    )
                }

                // 3. Download
                val downloadRoot = "/storage/emulated/0/Download"
                allBuckets.filter { it.relativePath.equals(downloadRoot, true) }.forEach {
                    val isPinned = if (hasExplicitPinnedPreference(it.id)) isAlbumPinned(it.id) else true
                    finalAlbums.add(it.copy(isPinned = isPinned))
                }

                // 4. WhatsApp
                val waId = "wa_root".hashCode().toLong()
                val waFolders = allBuckets.filter {
                    it.type == AlbumType.WHATSAPP || it.type == AlbumType.WHATSAPP_BUSINESS ||
                    it.relativePath.lowercase().contains("whatsapp")
                }
                if (waFolders.isNotEmpty()) {
                    val isPinned = if (hasExplicitPinnedPreference(waId)) isAlbumPinned(waId) else true
                    finalAlbums.add(
                        Album(waId, "WhatsApp", waFolders.first().coverUri,
                            waFolders.sumOf { it.itemCount }, -3, AlbumType.WHATSAPP,
                            "virtual/whatsapp", true, isPinned, isAlbumAnalysisEnabled(waId),
                            null, null, waFolders.maxOf { it.latestTimestamp },
                            waFolders.take(4).map { it.coverUri })
                    )
                }

                // 5. Galeria (Xiaomi Owner)
                val galId = "gal_root".hashCode().toLong()
                val ownerFolders = allBuckets.filter {
                    it.relativePath.lowercase().contains("/pictures/gallery/owner")
                }
                if (ownerFolders.isNotEmpty()) {
                    val isPinned = if (hasExplicitPinnedPreference(galId)) isAlbumPinned(galId) else true
                    finalAlbums.add(
                        Album(galId, "Galeria", ownerFolders.first().coverUri,
                            ownerFolders.sumOf { it.itemCount }, -5, AlbumType.FOLDER,
                            "virtual/galeria", true, isPinned, isAlbumAnalysisEnabled(galId),
                            null, null, ownerFolders.maxOf { it.latestTimestamp },
                            ownerFolders.take(4).map { it.coverUri })
                    )
                }

                // 6. Álbuns Virtuais do Usuário
                virtualAlbums.forEach { (name, bucketIds) ->
                    val vid = name.hashCode().toLong()
                    val isPinned = if (hasExplicitPinnedPreference(vid)) isAlbumPinned(vid) else true
                    val subAlbums = allBuckets.filter { it.bucketId in bucketIds }
                    if (subAlbums.isNotEmpty() || bucketIds.isEmpty()) {
                        val cover = subAlbums.firstOrNull()?.coverUri ?: Uri.EMPTY
                        finalAlbums.add(
                            Album(vid, name, cover, subAlbums.sumOf { it.itemCount },
                                -10, AlbumType.FOLDER, "virtual/user/$name", true, isPinned,
                                isAlbumAnalysisEnabled(vid), name, null,
                                subAlbums.maxOfOrNull { it.latestTimestamp } ?: 0L,
                                subAlbums.take(4).map { it.coverUri })
                        )
                    }
                }

                val groupedIds = (finalAlbums.map { it.id } +
                    waFolders.map { it.id } + recFolders.map { it.id } +
                    ownerFolders.map { it.id }).toSet()
                val others = allBuckets.filter { it.id !in groupedIds }

                return@withContext (finalAlbums + others).distinctBy { it.id }
            } else {
                val res = mutableListOf<Album>()
                when {
                    parentPath == "virtual/whatsapp" -> res.addAll(allBuckets.filter {
                        it.type == AlbumType.WHATSAPP || it.type == AlbumType.WHATSAPP_BUSINESS ||
                        it.relativePath.lowercase().contains("whatsapp")
                    })
                    parentPath == "virtual/recordings" -> res.addAll(allBuckets.filter {
                        it.relativePath.lowercase().contains("dcim/screenshots") ||
                        it.relativePath.lowercase().contains("dcim/screenrecorder") ||
                        it.type == AlbumType.SCREENSHOTS
                    })
                    parentPath == "virtual/galeria" -> res.addAll(allBuckets.filter {
                        it.relativePath.lowercase().contains("/pictures/gallery/owner")
                    })
                    parentPath.startsWith("virtual/user/") -> {
                        val virtualName = parentPath.removePrefix("virtual/user/")
                        val bucketIds = getUserVirtualAlbums()[virtualName] ?: emptyList()
                        res.addAll(allBuckets.filter { it.bucketId in bucketIds })
                    }
                    else -> res.addAll(allBuckets.filter { it.relativePath.startsWith(parentPath) })
                }
                return@withContext res.sortedByDescending { it.latestTimestamp }
            }
        }

    suspend fun getMediaByBucket(bucketId: Long): List<MediaItem> = withContext(Dispatchers.IO) {
        val sel = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val arg = arrayOf(bucketId.toString())
        val items = mutableListOf<MediaItem>()
        items.addAll(queryMediaWithSelection(imageUri, sel, arg))
        items.addAll(queryMediaWithSelection(videoUri, sel, arg))
        items.sortedByDescending { it.dateAdded }
    }

    fun isAlbumAnalysisEnabled(id: Long): Boolean =
        prefs.getBoolean("album_analysis_$id", id == -999L || id == -1739773001L)

    fun isAlbumPinned(id: Long): Boolean = prefs.getBoolean("album_pinned_$id", false)

    fun groupByDate(items: List<MediaItem>): List<MediaGroup> {
        if (items.isEmpty()) return emptyList()
        // BUG FIX: SimpleDateFormat não é thread-safe — instância local por chamada
        val longFmt = SimpleDateFormat("d 'de' MMMM", Locale("pt", "BR"))
        val groups = LinkedHashMap<String, MutableList<MediaItem>>()
        val cal = Calendar.getInstance()
        for (item in items) {
            cal.timeInMillis = item.dateAdded
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val label = longFmt.format(Date(cal.timeInMillis))
            groups.getOrPut(label) { mutableListOf() }.add(item)
        }
        return groups.map { MediaGroup(it.key, it.value) }
    }

    suspend fun getFavorites(): List<MediaItem> = getAllMedia(MediaFilter.FAVORITES)
    suspend fun getAllAlbumsFlat(): List<Album> = getAlbums()

    private data class AlbumBuilder(
        val bid: Long, val name: String, var coverUri: Uri,
        val path: String, var count: Int = 0, var latestTimestamp: Long = 0
    ) {
        fun toAlbum(repo: MediaRepository): Album {
            val cn = repo.prefs.getString("album_name_$bid", null)
            val cc = repo.prefs.getString("album_cover_$bid", null)
            return Album(
                bid, cn ?: name, cc?.let { Uri.parse(it) } ?: coverUri,
                count, bid, name.toAlbumType(), path, false,
                repo.isAlbumPinned(bid), repo.isAlbumAnalysisEnabled(bid),
                cn, cc?.let { Uri.parse(it) }, latestTimestamp, emptyList()
            )
        }
    }
}

enum class MediaFilter { ALL, CAMERA, PHOTOS, VIDEOS, SCREENSHOTS, FAVORITES }
