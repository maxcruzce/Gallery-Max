package com.hypergallery.data

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

data class ImageMetadata(
    val mediaId: Long = 0,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val exifData: ExifData? = null
)

data class ExifData(
    val make: String = "",
    val model: String = "",
    val aperture: String = "",
    val shutterSpeed: String = "",
    val iso: String = "",
    val focalLength: String = "",
    val flash: String = "",
    val dateTime: String = "",
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null
)

class MetadataRepository(context: Context) {
    private val prefs = context.getSharedPreferences("image_metadata", Context.MODE_PRIVATE)
    
    fun saveMetadata(mediaId: Long, description: String, tags: List<String>) {
        val metadata = getMetadata(mediaId).copy(
            description = description,
            tags = tags
        )
        saveToPrefs(mediaId, metadata)
    }
    
    fun getMetadata(mediaId: Long): ImageMetadata {
        val json = prefs.getString(mediaId.toString(), null)
        return if (json != null) {
            try {
                val obj = JSONObject(json)
                val tagsArray = obj.optJSONArray("tags") 
                val tagsList = if (tagsArray != null) {
                    (0 until tagsArray.length()).map { tagsArray.getString(it) }
                } else emptyList()
                
                ImageMetadata(
                    mediaId = obj.getLong("mediaId"),
                    description = obj.optString("description", ""),
                    tags = tagsList
                )
            } catch (e: Exception) {
                ImageMetadata(mediaId = mediaId)
            }
        } else {
            ImageMetadata(mediaId = mediaId)
        }
    }
    
    fun getAllMetadata(): Map<Long, ImageMetadata> {
        val all = mutableMapOf<Long, ImageMetadata>()
        prefs.all.forEach { (key, value) ->
            val mediaId = key.toLongOrNull() ?: return@forEach
            all[mediaId] = getMetadata(mediaId)
        }
        return all
    }
    
    fun deleteMetadata(mediaId: Long) {
        prefs.edit().remove(mediaId.toString()).apply()
    }
    
    private fun saveToPrefs(mediaId: Long, metadata: ImageMetadata) {
        val obj = JSONObject().apply {
            put("mediaId", metadata.mediaId)
            put("description", metadata.description)
            put("tags", JSONArray(metadata.tags))
        }
        prefs.edit().putString(mediaId.toString(), obj.toString()).apply()
    }
    
    fun readExifFromUri(context: Context, uri: Uri): ExifData? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                readExif(inputStream)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun readExif(inputStream: InputStream): ExifData? {
        return try {
            val exif = ExifInterface(inputStream)
            ExifData(
                make = exif.getAttribute(ExifInterface.TAG_MAKE) ?: "",
                model = exif.getAttribute(ExifInterface.TAG_MODEL) ?: "",
                aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER) ?: "",
                shutterSpeed = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME) ?: "",
                iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY) ?: "",
                focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH) ?: "",
                flash = exif.getAttribute(ExifInterface.TAG_FLASH) ?: "",
                dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME) ?: "",
                gpsLatitude = getGpsLatitude(exif),
                gpsLongitude = getGpsLongitude(exif)
            )
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getGpsLatitude(exif: ExifInterface): Double? {
        val lat = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
        val ref = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
        return if (lat != null && ref != null) {
            convertGpsToDouble(lat, ref)
        } else null
    }
    
    private fun getGpsLongitude(exif: ExifInterface): Double? {
        val lon = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
        val ref = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)
        return if (lon != null && ref != null) {
            convertGpsToDouble(lon, ref)
        } else null
    }
    
    private fun convertGpsToDouble(coordinate: String, ref: String): Double? {
        return try {
            val parts = coordinate.split(",")
            if (parts.size == 3) {
                val degrees = parts[0].trim().toDouble()
                val minutes = parts[1].trim().toDouble()
                val seconds = parts[2].trim().toDouble()
                var result = degrees + minutes / 60 + seconds / 3600
                if (ref == "S" || ref == "W") result *= -1
                result
            } else null
        } catch (e: Exception) {
            null
        }
    }
}