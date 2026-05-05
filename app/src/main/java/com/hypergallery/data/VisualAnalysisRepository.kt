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

    // ── ML Kit clients — thread-safe, reutilizados ─────────────────────────
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    // IMPORTANTE: Usa ACCURATE para melhor detecção de rostos, especialmente
    // rostos parciais, inclinados ou em grupo
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.10f) // Detecta rostos a partir de 10% do frame
            .enableTracking()
            .build()
    )

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val prefs = context.getSharedPreferences("visual_analysis", Context.MODE_PRIVATE)
    val faceRepository = FaceRepository(context)

    // ── TFLite FaceNet — opcional, degrada graciosamente se ausente ────────
    @Volatile private var faceNetInterpreter: Interpreter? = null
    val hasFaceNetModel: Boolean get() = faceNetInterpreter != null

    init {
        loadFaceNetModel()
    }

    private fun loadFaceNetModel() {
        try {
            val modelFile = context.assets.openFd("mobilefacenet.tflite")
            FileInputStream(modelFile.fileDescriptor).use { fis ->
                val channel = fis.channel
                val buffer = channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    modelFile.startOffset,
                    modelFile.declaredLength
                )
                faceNetInterpreter = Interpreter(buffer)
                Log.d(TAG, "FaceNet model loaded successfully")
            }
        } catch (e: Exception) {
            Log.w(TAG, "FaceNet model not found in assets (mobilefacenet.tflite). " +
                "Face grouping will work with approximate position-based matching.")
            faceNetInterpreter = null
        }
    }

    // ── Análise principal ──────────────────────────────────────────────────

    suspend fun analyzeImage(uri: Uri, mediaId: Long): AnalysisResult = withContext(Dispatchers.IO) {
        // Se já analisado, retorna cache
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

        try {
            sourceBitmap = loadDownscaledBitmap(uri)
                ?: return@withContext saveAndReturn(mediaId, AnalysisResult())

            val imageRotation = getExifRotation(uri)
            uprightBitmap = rotateBitmapIfNeeded(sourceBitmap, imageRotation)
            // sourceBitmap pode ter sido reciclado dentro de rotateBitmapIfNeeded
            // zeramos a referência para evitar double-recycle no finally
            if (uprightBitmap !== sourceBitmap) sourceBitmap = null

            // ── Detecção de rostos em múltiplas orientações ────────────────
            val (detectedFaces, faceBitmap) = detectFacesMultiAngle(uprightBitmap!!, mediaId, uri, imageRotation)

            // ── OCR + Labels no bitmap upright ────────────────────────────
            val bitmapForMl = faceBitmap ?: uprightBitmap!!
            val imageForMl = InputImage.fromBitmap(bitmapForMl, 0)

            val visionText = textRecognizer.process(imageForMl).await()
            val mlLabels = labeler.process(imageForMl).await()
            val labelStrings = mlLabels.map { it.text }

            val hasPets = mlLabels.any {
                val l = it.text.lowercase()
                l.contains("dog") || l.contains("cat") || l.contains("pet") ||
                l.contains("animal") || l.contains("bird")
            }

            val result = AnalysisResult(
                labels = labelStrings,
                text = visionText.text,
                faceCount = detectedFaces.size,
                faces = detectedFaces.map { it.boundingBox },
                hasPets = hasPets
            )

            // Recicla bitmap extra do loop de rotação
            if (faceBitmap != null && faceBitmap !== uprightBitmap) {
                faceBitmap.recycle()
            }

            saveAndReturn(mediaId, result)
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing image $mediaId: ${e.message}")
            AnalysisResult()
        } finally {
            try { sourceBitmap?.let { if (!it.isRecycled) it.recycle() } } catch (_: Exception) {}
            try { uprightBitmap?.let { if (!it.isRecycled) it.recycle() } } catch (_: Exception) {}
        }
    }

    /**
     * Detecta rostos testando 4 rotações do bitmap.
     * Retorna a lista de faces detectadas e o bitmap na orientação correta.
     */
    private suspend fun detectFacesMultiAngle(
        uprightBitmap: Bitmap,
        mediaId: Long,
        uri: Uri,
        imageRotation: Int
    ): Pair<List<Face>, Bitmap?> {

        val rotations = listOf(0, 90, 180, 270)

        for (rot in rotations) {
            val currentBitmap = if (rot == 0) {
                uprightBitmap
            } else {
                val matrix = android.graphics.Matrix().apply { postRotate(rot.toFloat()) }
                Bitmap.createBitmap(uprightBitmap, 0, 0, uprightBitmap.width, uprightBitmap.height, matrix, true)
            }

            val image = InputImage.fromBitmap(currentBitmap, 0)
            val faces = faceDetector.process(image).await()

            if (faces.isNotEmpty()) {
                Log.d(TAG, "[${mediaId}] Found ${faces.size} faces at rotation=$rot")

                faces.forEach { face ->
                    processFaceDetection(
                        face = face,
                        bitmap = currentBitmap,
                        mediaId = mediaId,
                        uri = uri,
                        totalRotation = imageRotation + rot
                    )
                }

                return faces to (if (rot != 0) currentBitmap else null)
            } else {
                // Recicla bitmaps rotacionados que não encontraram nada
                if (rot != 0) currentBitmap.recycle()
            }
        }

        return emptyList<Face>() to null
    }

    /**
     * Processa uma face detectada: extrai embedding e registra no FaceRepository.
     */
    private fun processFaceDetection(
        face: Face,
        bitmap: Bitmap,
        mediaId: Long,
        uri: Uri,
        totalRotation: Int
    ) {
        val bounds = face.boundingBox
        val normalizedBounds = NormalizedRect(
            left = bounds.left.toFloat() / bitmap.width,
            top = bounds.top.toFloat() / bitmap.height,
            right = bounds.right.toFloat() / bitmap.width,
            bottom = bounds.bottom.toFloat() / bitmap.height
        )

        // Extrai embedding (se modelo disponível) ou usa fingerprint posicional
        val embedding = if (faceNetInterpreter != null) {
            extractFaceNetEmbedding(bitmap, bounds)
        } else {
            // Fallback: usa características da posição/tamanho relativo como embedding
            // Isso permite agrupamento básico mesmo sem o modelo TFLite
            extractPositionalEmbedding(face, bitmap)
        }

        faceRepository.addFace(
            mediaId = mediaId,
            uri = uri,
            bounds = bounds,
            normalizedBounds = normalizedBounds,
            rotationDegrees = totalRotation,
            embedding = embedding
        )
    }

    /**
     * Embedding baseado em características posicionais e de face landmarks.
     * Usado quando o modelo FaceNet não está disponível.
     * Dimensão: 16 floats
     */
    private fun extractPositionalEmbedding(face: Face, bitmap: Bitmap): FloatArray {
        val bounds = face.boundingBox
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()

        // Características normalizadas da face
        val cx = bounds.centerX() / bw          // Centro X
        val cy = bounds.centerY() / bh          // Centro Y
        val w = bounds.width() / bw             // Largura relativa
        val h = bounds.height() / bh            // Altura relativa
        val aspectRatio = if (h > 0) w / h else 1f
        val area = w * h

        // Classificação de sorrisos e olhos abertos (ML Kit fornece)
        val smiling = face.smilingProbability ?: 0.5f
        val leftEyeOpen = face.leftEyeOpenProbability ?: 0.5f
        val rightEyeOpen = face.rightEyeOpenProbability ?: 0.5f

        // Ângulos de Euler
        val eulerY = (face.headEulerAngleY + 90f) / 180f  // normalizado 0-1
        val eulerZ = (face.headEulerAngleZ + 90f) / 180f
        val eulerX = (face.headEulerAngleX + 90f) / 180f

        // Landmarks normalizados (se disponíveis)
        fun landmark(type: Int): Pair<Float, Float>? {
            val lm = face.getLandmark(type) ?: return null
            return lm.position.x / bw to lm.position.y / bh
        }

        val nose = landmark(com.google.mlkit.vision.face.FaceLandmark.NOSE_BASE)
        val leftEar = landmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EAR)
        val rightEar = landmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EAR)
        val leftMouth = landmark(com.google.mlkit.vision.face.FaceLandmark.MOUTH_LEFT)
        val rightMouth = landmark(com.google.mlkit.vision.face.FaceLandmark.MOUTH_RIGHT)

        // Distância entre orelhas (proporção do rosto)
        val earSpan = if (leftEar != null && rightEar != null) {
            kotlin.math.sqrt(
                ((rightEar.first - leftEar.first).pow2() + (rightEar.second - leftEar.second).pow2()).toDouble()
            ).toFloat()
        } else w

        // Largura da boca relativa ao rosto
        val mouthWidth = if (leftMouth != null && rightMouth != null) {
            kotlin.math.sqrt(
                ((rightMouth.first - leftMouth.first).pow2() + (rightMouth.second - leftMouth.second).pow2()).toDouble()
            ).toFloat() / (earSpan + 1e-6f)
        } else 0.5f

        return floatArrayOf(
            cx, cy, w, h, aspectRatio, area,
            smiling, leftEyeOpen, rightEyeOpen,
            eulerY, eulerZ, eulerX,
            nose?.first ?: cx, nose?.second ?: cy,
            earSpan, mouthWidth
        )
    }

    private fun Float.pow2() = this * this

    /**
     * Extrai embedding usando o modelo FaceNet (MobileFaceNet).
     * Entrada: bitmap 112x112 RGB normalizado.
     * Saída: vetor 128 ou 192 dims L2-normalizado.
     */
    private fun extractFaceNetEmbedding(bitmap: Bitmap, bounds: Rect): FloatArray? {
        val interpreter = faceNetInterpreter ?: return null
        return try {
            // Padding de 20% para incluir contexto facial
            val padding = 0.20f
            val padW = ((bounds.right - bounds.left) * padding).toInt()
            val padH = ((bounds.bottom - bounds.top) * padding).toInt()

            val safeLeft   = (bounds.left   - padW).coerceIn(0, bitmap.width - 1)
            val safeTop    = (bounds.top    - padH).coerceIn(0, bitmap.height - 1)
            val safeRight  = (bounds.right  + padW).coerceIn(safeLeft + 1, bitmap.width)
            val safeBottom = (bounds.bottom + padH).coerceIn(safeTop + 1, bitmap.height)

            val faceBitmap = Bitmap.createBitmap(
                bitmap, safeLeft, safeTop,
                safeRight - safeLeft, safeBottom - safeTop
            )
            val resized = Bitmap.createScaledBitmap(faceBitmap, 112, 112, true)
            faceBitmap.recycle()

            val inputBuffer = ByteBuffer.allocateDirect(1 * 112 * 112 * 3 * 4)
            inputBuffer.order(ByteOrder.nativeOrder())

            val pixels = IntArray(112 * 112)
            resized.getPixels(pixels, 0, 112, 0, 0, 112, 112)
            resized.recycle()

            for (pixel in pixels) {
                inputBuffer.putFloat(((pixel shr 16 and 0xFF).toFloat() - 127.5f) / 128.0f)
                inputBuffer.putFloat(((pixel shr 8  and 0xFF).toFloat() - 127.5f) / 128.0f)
                inputBuffer.putFloat(((pixel        and 0xFF).toFloat() - 127.5f) / 128.0f)
            }
            inputBuffer.rewind()

            val outputSize = interpreter.getOutputTensor(0).shape()[1]
            val output = Array(1) { FloatArray(outputSize) }
            interpreter.run(inputBuffer, output)

            l2Normalize(output[0])
        } catch (e: Exception) {
            Log.e(TAG, "FaceNet embedding error", e)
            null
        }
    }

    private fun l2Normalize(v: FloatArray): FloatArray {
        var norm = 0f
        for (x in v) norm += x * x
        norm = kotlin.math.sqrt(norm.toDouble()).toFloat()
        if (norm > 1e-10f) for (i in v.indices) v[i] /= norm
        return v
    }

    // ── Utilitários de bitmap ──────────────────────────────────────────────

    private fun loadDownscaledBitmap(uri: Uri): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null

            val targetSize = 1024
            var inSampleSize = 1
            if (opts.outHeight > targetSize || opts.outWidth > targetSize) {
                while ((opts.outHeight / (inSampleSize * 2)) >= targetSize &&
                       (opts.outWidth  / (inSampleSize * 2)) >= targetSize) {
                    inSampleSize *= 2
                }
            }

            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                    this.inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap: $uri", e)
            null
        }
    }

    private fun rotateBitmapIfNeeded(bitmap: Bitmap, rotation: Int): Bitmap {
        if (rotation == 0) return bitmap
        val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun getExifRotation(uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use {
                val exif = ExifInterface(it)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90  -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) { 0 }
    }

    // ── Persistência de resultados ─────────────────────────────────────────

    private fun saveAndReturn(mediaId: Long, result: AnalysisResult): AnalysisResult {
        prefs.edit().apply {
            putString("labels_$mediaId", result.labels.joinToString(","))
            putString("text_$mediaId", result.text)
            putInt("faces_$mediaId", result.faceCount)
            putString(
                "face_bounds_$mediaId",
                result.faces.joinToString(";") { "${it.left},${it.top},${it.right},${it.bottom}" }
            )
            putBoolean("pets_$mediaId", result.hasPets)
            apply()
        }
        return result
    }

    // ── Leitura do cache ───────────────────────────────────────────────────

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
            val p = it.split(",")
            if (p.size == 4) try {
                Rect(p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toInt())
            } catch (_: Exception) { null }
            else null
        }
    }

    fun getAllAnalyzedIds(): List<Long> {
        return prefs.all.keys
            .filter { it.startsWith("labels_") }
            .mapNotNull { it.removePrefix("labels_").toLongOrNull() }
    }

    fun isAnalyzed(mediaId: Long): Boolean =
        prefs.contains("labels_$mediaId")

    /**
     * Força re-análise de uma imagem (limpa cache e reanalisa).
     */
    suspend fun reanalyzeImage(uri: Uri, mediaId: Long): AnalysisResult {
        prefs.edit()
            .remove("labels_$mediaId")
            .remove("text_$mediaId")
            .remove("faces_$mediaId")
            .remove("face_bounds_$mediaId")
            .remove("pets_$mediaId")
            .apply()
        return analyzeImage(uri, mediaId)
    }
}
