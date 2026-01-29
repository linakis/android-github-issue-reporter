package com.ghreporter.utils

import org.junit.Assert.*
import org.junit.Test

class ImageUtilsTest {

    @Test
    fun `toDataUri creates correct data URI format`() {
        // Given
        val base64 = "SGVsbG8gV29ybGQ=" // "Hello World" in base64

        // When
        val dataUri = ImageUtils.toDataUri(base64)

        // Then
        assertEquals("data:image/jpeg;base64,SGVsbG8gV29ybGQ=", dataUri)
    }

    @Test
    fun `toDataUri with custom mimeType`() {
        // Given
        val base64 = "SGVsbG8gV29ybGQ="

        // When
        val dataUri = ImageUtils.toDataUri(base64, "image/png")

        // Then
        assertEquals("data:image/png;base64,SGVsbG8gV29ybGQ=", dataUri)
    }

    @Test
    fun `toMarkdownImage creates correct markdown format`() {
        // Given
        val base64 = "SGVsbG8gV29ybGQ="

        // When
        val markdown = ImageUtils.toMarkdownImage(base64)

        // Then
        assertEquals("![Screenshot](data:image/jpeg;base64,SGVsbG8gV29ybGQ=)", markdown)
    }

    @Test
    fun `toMarkdownImage with custom alt text`() {
        // Given
        val base64 = "SGVsbG8gV29ybGQ="

        // When
        val markdown = ImageUtils.toMarkdownImage(base64, "App Crash Screenshot")

        // Then
        assertEquals("![App Crash Screenshot](data:image/jpeg;base64,SGVsbG8gV29ybGQ=)", markdown)
    }

    @Test
    fun `ProcessedImage data class holds correct values`() {
        // Given/When
        val processed = ImageUtils.ProcessedImage(
            base64 = "test-base64",
            mimeType = "image/jpeg",
            originalWidth = 1080,
            originalHeight = 1920,
            resizedWidth = 480,
            resizedHeight = 853,
            sizeBytes = 12345
        )

        // Then
        assertEquals("test-base64", processed.base64)
        assertEquals("image/jpeg", processed.mimeType)
        assertEquals(1080, processed.originalWidth)
        assertEquals(1920, processed.originalHeight)
        assertEquals(480, processed.resizedWidth)
        assertEquals(853, processed.resizedHeight)
        assertEquals(12345, processed.sizeBytes)
    }
}
