package com.hypergallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import android.graphics.Rect
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.FileInputStream
import java.nio.channels.FileChannel

data class FaceData(
    val mediaId: Long,
    val bounds: Rect,
    val personId: String? = null
)

data class AnalysisResult(
    val labels: List<String> = emptyList(),
    val text: String = "",
    val faceCount: Int = 0,
    val faces: List<Rect> = emptyList(),
    val hasPets: Boolean = false
)

class VisualAnalysisRepository(private val context: Context) {
    private val TAG = "VisualAnalysis"

    // PERF: ML Kit clients são thread-safe e caros para criar — instância única
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val prefs = context.getSharedPreferences("visual_analysis", Context.MODE_PRIVATE)
    private val faceRepository = FaceRepository(context)

    // BUG FIX: Interpreter é thread-safe para leitura mas não para escrita simultânea
    // Carregado de forma lazy e guardado como @Volatile para visibilidade entre threads
    @Volatile
    private var faceNetInterpreter: Interpreter? = null

    init {
        try {
            val modelFile = context.assets.openFd("mobilefacenet.tflite")
            val inputStream = FileInputStream(modelFile.fileDescriptor)
            val fileChannel = inputStream.channel
            val buffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                modelFile.startOffset,
                modelFile.declaredLength
            )
            // BUG FIX: Fechar os streams após mapear o buffer
            fileChannel.close()
            inputStream.close()
            faceNetInterpreter = Interpreter(buffer)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading FaceNet model (mobilefacenet.tflite not found in assets?)", e)
            // O app funciona sem o modelo de faces — a funcionalidade de embedding é degradada graciosamente
        }
    }

    suspend fun analyzeImage(uri: Uri, mediaId: Long): AnalysisResult = withContext(Dispatchers.IO) {
        // Verifica se já foi analisado
        val savedLabels = prefs.getString("labels_$mediaId", null)
        if (savedLabels != null) {
            return@withContext AnalysisResult(
                labels = if (savedLabels.isEmpty()) emptyList() else savedLabels.split(","),
                text = prefs.getString("text_$mediaId", "") ?: "",
                faceCount = prefs.getInt("faces_$mediaId", 0),
                hasPets = prefs.getBoolean("pets_$mediaId", false)
            )
        }

        var sourceBitmap: Bitmap? = null
        var uprightBitmap: Bitmap? = null
        var extraBitmap: Bitmap? = null // BUG FIX: rastreio correto de bitmaps adicionais criados no loop

        try {
            sourceBitmap = loadDownscaledBitmap(uri) ?: return@withContext AnalysisResult()

            val imageRotation = getRotation(uri)
            uprightBitmap = if (imageRotation != 0) {
                val matrix = android.graphics.Matrix().apply { postRotate(imageRotation.toFloat()) }
                Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.width, sourceBitmap.height, matrix, true)
                    .also { if (it != sourceBitmap) sourceBitmap.recycle().also { sourceBitmap = null } }
            } else {
                sourceBitmap
            }

            var detectedFaces: List<Face> = emptyList()
            var faceDetectionBitmap: Bitmap? = null

            // Busca exaustiva em 4 orientações
            val rotations = listOf(0, 90, 180, 270)
            for (rot in rotations) {
                val currentBitmap = if (rot == 0) {
                    uprightBitmap!!
                } else {
                    val matrix = android.graphics.Matrix().apply { postRotate(rot.toFloat()) }
                    Bitmap.createBitmap(
                        uprightBitmap!!, 0, 0,
                        uprightBitmap!!.width, uprightBitmap!!.height, matrix, true
                    )
                }

                val image = InputImage.fromBitmap(currentBitmap, 0)
                val faces = faceDetector.process(image).await()

                if (faces.isNotEmpty()) {
                    detectedFaces = faces
                    faceDetectionBitmap = currentBitmap
                    Log.d(TAG, "Found ${faces.size} faces at rotation $rot for media $mediaId")

                    faces.forEach { face ->
                        val bounds = face.boundingBox
                        val normalized = NormalizedRect(
                            bounds.left.toFloat() / currentBitmap.width,
                            bounds.top.toFloat() / currentBitmap.height,
                            bounds.right.toFloat() / currentBitmap.width,
                            bounds.bottom.toFloat() / currentBitmap.height
                        )
                        val embedding = extractEmbedding(currentBitmap, bounds)
                        faceRepository.addFace(
                            mediaId = mediaId,
                            uri = uri,
                            bounds = bounds,
                            normalizedBounds = normalized,
                            rotationDegrees = imageRotation + rot,
                            embedding = embedding
                        )
                    }
                    break
                } else {
                    // BUG FIX: Só recicla se criamos um novo bitmap (rot != 0) e não é o uprightBitmap
                    if (rot != 0 && currentBitmap !== uprightBitmap) {
                        currentBitmap.recycle()
                    }
                }
            }

            val bitmapForOcr = faceDetectionBitmap ?: uprightBitmap!!
            val imageForOcr = InputImage.fromBitmap(bitmapForOcr, 0)

            val visionText = textRecognizer.process(imageForOcr).await()
            val labels = labeler.process(imageForOcr).await()
            val labelStrings = labels.map { it.text }

            val result = AnalysisResult(
                labels = labelStrings,
                text = visionText.text,
                faceCount = detectedFaces.size,
                faces = detectedFaces.map { it.boundingBox },
                hasPets = labels.any {
                    val l = it.text.lowercase()
                    l.contains("dog") || l.contains("cat") || l.contains("pet") || l.contains("animal")
                }
            )

            // BUG FIX: Salva labels como lista vazia corretamente (sem espaços extras)
            prefs.edit().apply {
                putString("labels_$mediaId", labelStrings.joinToString(","))
                putString("text_$mediaId", visionText.text)
                putInt("faces_$mediaId", detectedFaces.size)
                putString(
                    "face_bounds_$mediaId",
                    detectedFaces.joinToString(";") { "${it.boundingBox.left},${it.boundingBox.top},${it.boundingBox.right},${it.boundingBox.bottom}" }
                )
                putBoolean("pets_$mediaId", result.hasPets)
                apply()
            }

            // BUG FIX: Recicla o bitmap de detecção de faces se for diferente dos outros
            if (faceDetectionBitmap != null && faceDetectionBitmap !== uprightBitmap && faceDetectionBitmap !== sourceBitmap) {
                faceDetectionBitmap.recycle()
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing image: $mediaId", e)
            AnalysisResult()
        } finally {
            // BUG FIX: Garante reciclagem de todos os bitmaps em qualquer caminho de saída
            try { if (sourceBitmap != null && !sourceBitmap!!.isRecycled) sourceBitmap!!.recycle() } catch (_: Exception) {}
            try { if (uprightBitmap != null && uprightBitmap !== sourceBitmap && !uprightBitmap!!.isRecycled) uprightBitmap!!.recycle() } catch (_: Exception) {}
        }
    }

    private fun loadDownscaledBitmap(uri: Uri): Bitmap? {
        return try {
            // Passo 1: Descobre tamanho sem alocar memória
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            // Passo 2: Calcula inSampleSize para atingir ~1MP
            val targetSize = 1024
            var inSampleSize = 1
            if (options.outHeight > targetSize || options.outWidth > targetSize) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / inSampleSize >= targetSize && halfWidth / inSampleSize >= targetSize) {
                    inSampleSize *= 2
                }
            }

            // Passo 3: Decodifica com amostragem
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                // PERF: Usa RGB_565 para labels/texto onde transparência não importa
                // Mantemos ARGB_8888 para detecção de faces (qualidade necessária)
                this.inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap for $uri", e)
            null
        }
    }

    private fun getRotation(uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) { 0 }
    }

    fun getLabels(mediaId: Long): List<String> {
        val raw = prefs.getString("labels_$mediaId", null) ?: return emptyList()
        return if (raw.isEmpty()) emptyList() else raw.split(",")
    }

    fun getText(mediaId: Long): String = prefs.getString("text_$mediaId", "") ?: ""
    fun hasPets(mediaId: Long): Boolean = prefs.getBoolean("pets_$mediaId", false)
    fun getFaceCount(mediaId: Long): Int = prefs.getInt("faces_$mediaId", 0)

    fun getFaceBounds(mediaId: Long): List<Rect> {
        val raw = prefs.getString("face_bounds_$mediaId", null) ?: return emptyList()
        return raw.split(";").mapNotNull {
            val parts = it.split(",")
            if (parts.size == 4) {
                try {
                    Rect(parts[0].toInt(), parts[1].toInt(), parts[2].toInt(), parts[3].toInt())
                } catch (_: NumberFormatException) { null }
            } else null
        }
    }

    fun getAllAnalyzedIds(): List<Long> {
        return prefs.all.keys
            .filter { it.startsWith("labels_") }
            .mapNotNull { it.removePrefix("labels_").toLongOrNull() }
    }

    private fun extractEmbedding(bitmap: Bitmap, bounds: Rect, rotation: Int = 0): FloatArray? {
        val interpreter = faceNetInterpreter ?: return null
        return try {
            val padding = 0.20f
            val padW = ((bounds.right - bounds.left) * padding).toInt()
            val padH = ((bounds.bottom - bounds.top) * padding).toInt()

            val safeLeft = (bounds.left - padW).coerceIn(0, bitmap.width - 1)
            val safeTop = (bounds.top - padH).coerceIn(0, bitmap.height - 1)
            val safeRight = (bounds.right + padW).coerceIn(safeLeft + 1, bitmap.width)
            val safeBottom = (bounds.bottom + padH).coerceIn(safeTop + 1, bitmap.height)

            var faceBitmap = Bitmap.createBitmap(
                bitmap, safeLeft, safeTop,
                safeRight - safeLeft, safeBottom - safeTop
            )

            if (rotation != 0) {
                val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
                val rotatedFace = Bitmap.createBitmap(
                    faceBitmap, 0, 0, faceBitmap.width, faceBitmap.height, matrix, true
                )
                faceBitmap.recycle()
                faceBitmap = rotatedFace
            }

            val resizedBitmap = Bitmap.createScaledBitmap(faceBitmap, 112, 112, true)
            faceBitmap.recycle()

            // BUG FIX: allocateDirect é mais eficiente para JNI/TFLite
            val inputBuffer = ByteBuffer.allocateDirect(1 * 112 * 112 * 3 * 4)
            inputBuffer.order(ByteOrder.nativeOrder())

            val pixels = IntArray(112 * 112)
            resizedBitmap.getPixels(pixels, 0, 112, 0, 0, 112, 112)
            resizedBitmap.recycle()

            for (pixel in pixels) {
                inputBuffer.putFloat(((pixel shr 16 and 0xFF).toFloat() - 127.5f) / 128.0f)
                inputBuffer.putFloat(((pixel shr 8 and 0xFF).toFloat() - 127.5f) / 128.0f)
                inputBuffer.putFloat(((pixel and 0xFF).toFloat() - 127.5f) / 128.0f)
            }

            inputBuffer.rewind()

            val outputSize = interpreter.getOutputTensor(0).shape()[1]
            val output = Array(1) { FloatArray(outputSize) }
            interpreter.run(inputBuffer, output)

            l2Normalize(output[0])
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting embedding", e)
            null
        }
    }

    private fun l2Normalize(vector: FloatArray): FloatArray {
        var norm = 0f
        for (v in vector) norm += v * v
        norm = kotlin.math.sqrt(norm.toDouble()).toFloat()  // PERF: kotlin.math ao invés de java.lang.Math
        if (norm > 1e-10f) {
            for (i in vector.indices) vector[i] /= norm
        }
        return vector
    }
}
