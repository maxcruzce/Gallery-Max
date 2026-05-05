package com.hypergallery.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.transform.Transformation
import com.hypergallery.data.FaceInfo
import com.hypergallery.data.NormalizedRect

@Composable
fun FaceThumbnail(face: FaceInfo, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(face.mediaUri)
            .transformations(
                if (face.normalizedBounds != null) {
                    CropTransformation(face.normalizedBounds, face.rotationDegrees)
                } else {
                    // Fallback to old bounds if normalized not available
                    LegacyCropTransformation(face.bounds, face.rotationDegrees)
                }
            )
            .crossfade(true)
            .build(),
        contentDescription = null,
        modifier = modifier.clickable { onClick() },
        contentScale = ContentScale.Crop
    )
}

class CropTransformation(private val norm: NormalizedRect, private val rotation: Int) : Transformation {
    override val cacheKey: String = "crop_norm_${norm.left}_${norm.top}_${norm.right}_${norm.bottom}_rot_$rotation"

    override suspend fun transform(input: Bitmap, size: coil.size.Size): Bitmap {
        // Para que o crop funcione com coordenadas de um rosto "em pé",
        // primeiro giramos a imagem inteira se necessário.
        val workingBitmap = if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(input, 0, 0, input.width, input.height, matrix, true)
        } else {
            input
        }

        val width = norm.right - norm.left
        val height = norm.bottom - norm.top
        
        // Adiciona padding de 25% para o rosto respirar
        val padW = width * 0.25f
        val padH = height * 0.25f
        
        val left = ((norm.left - padW).coerceIn(0f, 1f) * workingBitmap.width).toInt()
        val top = ((norm.top - padH).coerceIn(0f, 1f) * workingBitmap.height).toInt()
        val right = ((norm.right + padW).coerceIn(0f, 1f) * workingBitmap.width).toInt()
        val bottom = ((norm.bottom + padH).coerceIn(0f, 1f) * workingBitmap.height).toInt()
        
        val actualWidth = (right - left).coerceAtLeast(1)
        val actualHeight = (bottom - top).coerceAtLeast(1)
        
        val cropped = Bitmap.createBitmap(workingBitmap, left, top, actualWidth, actualHeight)
        
        // Se giramos a imagem inteira, limpamos o temporário
        if (workingBitmap != input) {
            workingBitmap.recycle()
        }
        
        return cropped
    }
}

class LegacyCropTransformation(private val bounds: Rect, private val rotation: Int) : Transformation {
    override val cacheKey: String = "crop_legacy_${bounds.left}_${bounds.top}_${bounds.right}_${bounds.bottom}_rot_$rotation"

    override suspend fun transform(input: Bitmap, size: coil.size.Size): Bitmap {
        // Se as coordenadas originais forem baseadas em 1024px, tentamos escalar para o bitmap atual
        val scaleX = input.width.toFloat() / 1024f
        val scaleY = input.height.toFloat() / 1024f
        
        val left = (bounds.left * scaleX).toInt().coerceIn(0, input.width - 1)
        val top = (bounds.top * scaleY).toInt().coerceIn(0, input.height - 1)
        val right = (bounds.right * scaleX).toInt().coerceIn(left + 1, input.width)
        val bottom = (bounds.bottom * scaleY).toInt().coerceIn(top + 1, input.height)
        
        val cropped = Bitmap.createBitmap(input, left, top, right - left, bottom - top)
        
        if (rotation == 0) return cropped
        
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true).also {
            if (it != cropped) cropped.recycle()
        }
    }
}
