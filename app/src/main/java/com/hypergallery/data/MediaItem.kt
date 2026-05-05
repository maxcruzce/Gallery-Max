package com.hypergallery.data

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val bucketName: String,
    val bucketId: Long,
    val dateTaken: Long,
    val dateAdded: Long,
    val size: Long,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val duration: Long = 0L,
    val isFavorite: Boolean = false,
    val text: String = ""
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isPhoto: Boolean get() = mimeType.startsWith("image/")
}

data class Album(
    val id: Long,
    val name: String,
    val coverUri: Uri,
    val itemCount: Int,
    val bucketId: Long,
    val type: AlbumType = AlbumType.FOLDER,
    val relativePath: String = "",
    val isVirtualFolder: Boolean = false,
    val isPinned: Boolean = false,
    val isAnalysisEnabled: Boolean = false, // Se deve procurar rostos neste álbum
    val customName: String? = null,
    val customCoverUri: Uri? = null,
    val latestTimestamp: Long = 0L,
    val mosaicUris: List<Uri> = emptyList()
)

enum class AlbumSort { DATE_DESC, DATE_ASC, NAME_ASC, NAME_DESC, SIZE_DESC }

enum class AlbumType {
    CAMERA,
    WHATSAPP,
    WHATSAPP_BUSINESS,
    INSTAGRAM,
    TELEGRAM,
    SCREENSHOTS,
    DOWNLOADS,
    FAVORITES,
    FOLDER
}

fun String.toAlbumType(): AlbumType {
    val name = this.lowercase()
    return when {
        name.contains("camera") || name.contains("câmera") || name.contains("dcim") -> AlbumType.CAMERA
        name.contains("whatsapp") || name == "sent" -> AlbumType.WHATSAPP
        name.contains("instagram") -> AlbumType.INSTAGRAM
        name.contains("telegram") -> AlbumType.TELEGRAM
        name.contains("screenshot") || name.contains("captura") -> AlbumType.SCREENSHOTS
        name.contains("download") -> AlbumType.DOWNLOADS
        else -> AlbumType.FOLDER
    }
}

data class MediaGroup(
    val label: String,
    val items: List<MediaItem>
)