package com.ghreporter.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Utility class for image processing.
 *
 * Handles resizing and Base64 encoding of screenshots for upload.
 */
object ImageUtils {

    private const val DEFAULT_MAX_WIDTH = 480
    private const val JPEG_QUALITY = 80

    /**
     * Result of processing an image.
     */
    data class ProcessedImage(
        val base64: String,
        val mimeType: String,
        val originalWidth: Int,
        val originalHeight: Int,
        val resizedWidth: Int,
        val resizedHeight: Int,
        val sizeBytes: Int
    )

    /**
     * Load, resize, and encode an image to Base64.
     *
     * @param context Android context
     * @param uri Image URI (from content picker)
     * @param maxWidth Maximum width in pixels (default 480)
     * @return ProcessedImage or null if loading failed
     */
    suspend fun processImage(
        context: Context,
        uri: Uri,
        maxWidth: Int = DEFAULT_MAX_WIDTH
    ): ProcessedImage? = withContext(Dispatchers.IO) {
        try {
            // First, decode bounds only to get dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            val originalWidth = options.outWidth
            val originalHeight = options.outHeight

            if (originalWidth <= 0 || originalHeight <= 0) {
                return@withContext null
            }

            // Calculate sample size for efficient loading
            val sampleSize = calculateSampleSize(originalWidth, originalHeight, maxWidth)

            // Decode with sample size
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }

            val sampledBitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: return@withContext null

            // Resize to exact max width if still larger
            val resizedBitmap = if (sampledBitmap.width > maxWidth) {
                val ratio = maxWidth.toFloat() / sampledBitmap.width
                val newHeight = (sampledBitmap.height * ratio).toInt()
                val scaled = Bitmap.createScaledBitmap(sampledBitmap, maxWidth, newHeight, true)
                if (scaled != sampledBitmap) {
                    sampledBitmap.recycle()
                }
                scaled
            } else {
                sampledBitmap
            }

            // Encode to JPEG Base64
            val outputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            val bytes = outputStream.toByteArray()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

            val result = ProcessedImage(
                base64 = base64,
                mimeType = "image/jpeg",
                originalWidth = originalWidth,
                originalHeight = originalHeight,
                resizedWidth = resizedBitmap.width,
                resizedHeight = resizedBitmap.height,
                sizeBytes = bytes.size
            )

            resizedBitmap.recycle()

            result
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Calculate the sample size for BitmapFactory to efficiently load large images.
     */
    private fun calculateSampleSize(width: Int, height: Int, maxWidth: Int): Int {
        var sampleSize = 1
        if (width > maxWidth) {
            val halfWidth = width / 2
            while ((halfWidth / sampleSize) >= maxWidth) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }

    /**
     * Create a Data URI from Base64 encoded image.
     * This can be used in markdown: ![screenshot](data:image/jpeg;base64,...)
     */
    fun toDataUri(base64: String, mimeType: String = "image/jpeg"): String {
        return "data:$mimeType;base64,$base64"
    }

    /**
     * Create markdown image tag with embedded Base64.
     * Note: This may not render in all markdown viewers.
     */
    fun toMarkdownImage(base64: String, alt: String = "Screenshot"): String {
        return "![$alt](data:image/jpeg;base64,$base64)"
    }
}
