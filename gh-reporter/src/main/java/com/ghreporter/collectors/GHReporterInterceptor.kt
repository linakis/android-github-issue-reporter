package com.ghreporter.collectors

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit

/**
 * An OkHttp Interceptor that collects network request/response logs for GHReporter.
 *
 * Uses a ring buffer to keep the most recent entries up to [maxEntries].
 * Thread-safe for concurrent network operations.
 *
 * Usage:
 * ```kotlin
 * val client = OkHttpClient.Builder()
 *     .addInterceptor(GHReporter.getOkHttpInterceptor())
 *     .build()
 * ```
 */
class GHReporterInterceptor(
    private val maxEntries: Int = 50,
    private val maxBodySize: Long = 64 * 1024 // 64 KB max body capture
) : Interceptor {

    private val logs = ConcurrentLinkedDeque<NetworkLogEntry>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /**
     * Represents a single network request/response pair.
     */
    data class NetworkLogEntry(
        val id: String,
        val timestamp: Long,
        val formattedTime: String,
        val method: String,
        val url: String,
        val requestHeaders: Map<String, String>,
        val requestBody: String?,
        val responseCode: Int?,
        val responseMessage: String?,
        val responseHeaders: Map<String, String>,
        val responseBody: String?,
        val durationMs: Long,
        val error: String?,
        val isSuccess: Boolean
    ) {
        /**
         * Format as a concise summary line.
         */
        fun formatSummary(): String {
            val status = if (isSuccess) "$responseCode" else "ERROR"
            return "$formattedTime | $method $url | $status | ${durationMs}ms"
        }

        /**
         * Format as a detailed log entry.
         */
        fun formatDetailed(): String = buildString {
            appendLine("═══════════════════════════════════════════════════════════════")
            appendLine("[$formattedTime] $method $url")
            appendLine("───────────────────────────────────────────────────────────────")

            // Request
            appendLine("REQUEST:")
            if (requestHeaders.isNotEmpty()) {
                requestHeaders.forEach { (k, v) ->
                    appendLine("  $k: ${redactSensitiveHeader(k, v)}")
                }
            }
            if (!requestBody.isNullOrBlank()) {
                appendLine("  Body: ${truncateBody(requestBody)}")
            }

            appendLine()

            // Response
            if (error != null) {
                appendLine("RESPONSE: ERROR")
                appendLine("  $error")
            } else {
                appendLine("RESPONSE: $responseCode $responseMessage (${durationMs}ms)")
                if (responseHeaders.isNotEmpty()) {
                    responseHeaders.forEach { (k, v) ->
                        appendLine("  $k: $v")
                    }
                }
                if (!responseBody.isNullOrBlank()) {
                    appendLine("  Body: ${truncateBody(responseBody)}")
                }
            }
        }

        private fun redactSensitiveHeader(key: String, value: String): String {
            val lowerKey = key.lowercase()
            return if (lowerKey.contains("authorization") ||
                lowerKey.contains("cookie") ||
                lowerKey.contains("token") ||
                lowerKey.contains("api-key") ||
                lowerKey.contains("apikey")) {
                "[REDACTED]"
            } else {
                value
            }
        }

        private fun truncateBody(body: String, maxLength: Int = 1000): String {
            return if (body.length > maxLength) {
                body.take(maxLength) + "... [truncated, ${body.length} chars total]"
            } else {
                body
            }
        }
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestId = UUID.randomUUID().toString().take(8)
        val startTime = System.currentTimeMillis()
        val now = Date(startTime)

        val requestHeaders = extractHeaders(request)
        val requestBody = extractRequestBody(request)

        var response: Response? = null
        var error: String? = null

        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            error = "${e.javaClass.simpleName}: ${e.message}"
            throw e
        } finally {
            val durationMs = System.currentTimeMillis() - startTime

            val entry = NetworkLogEntry(
                id = requestId,
                timestamp = startTime,
                formattedTime = dateFormat.format(now),
                method = request.method,
                url = request.url.toString(),
                requestHeaders = requestHeaders,
                requestBody = requestBody,
                responseCode = response?.code,
                responseMessage = response?.message,
                responseHeaders = response?.let { extractResponseHeaders(it) } ?: emptyMap(),
                responseBody = response?.let { extractResponseBody(it) },
                durationMs = durationMs,
                error = error,
                isSuccess = response?.isSuccessful == true
            )

            logs.addLast(entry)

            // Trim to max size
            while (logs.size > maxEntries) {
                logs.pollFirst()
            }
        }

        return response!!
    }

    private fun extractHeaders(request: Request): Map<String, String> {
        return request.headers.toMap()
    }

    private fun extractResponseHeaders(response: Response): Map<String, String> {
        return response.headers.toMap()
    }

    private fun extractRequestBody(request: Request): String? {
        val body = request.body ?: return null

        return try {
            val buffer = Buffer()
            body.writeTo(buffer)
            
            if (buffer.size > maxBodySize) {
                "[Body too large: ${buffer.size} bytes]"
            } else {
                buffer.readUtf8()
            }
        } catch (e: Exception) {
            "[Unable to read body: ${e.message}]"
        }
    }

    private fun extractResponseBody(response: Response): String? {
        val body = response.body ?: return null
        val contentType = body.contentType()

        // Skip binary content
        val subtype = contentType?.subtype?.lowercase() ?: ""
        if (subtype.contains("octet-stream") ||
            subtype.contains("image") ||
            subtype.contains("video") ||
            subtype.contains("audio")) {
            return "[Binary content: ${contentType}]"
        }

        return try {
            val source = body.source()
            source.request(maxBodySize)
            val buffer = source.buffer

            if (buffer.size > maxBodySize) {
                "[Body too large: ${buffer.size} bytes]"
            } else {
                buffer.clone().readUtf8()
            }
        } catch (e: Exception) {
            "[Unable to read body: ${e.message}]"
        }
    }

    private fun okhttp3.Headers.toMap(): Map<String, String> {
        return (0 until size).associate { name(it) to value(it) }
    }

    /**
     * Get all collected network log entries.
     *
     * @return List of entries, oldest first
     */
    fun getLogs(): List<NetworkLogEntry> = logs.toList()

    /**
     * Get logs formatted as summaries.
     */
    fun getLogsAsSummary(): String {
        return logs.joinToString("\n") { it.formatSummary() }
    }

    /**
     * Get logs formatted with full details.
     */
    fun getLogsAsDetailed(): String {
        return logs.joinToString("\n\n") { it.formatDetailed() }
    }

    /**
     * Get the number of collected logs.
     */
    fun size(): Int = logs.size

    /**
     * Clear all collected logs.
     */
    fun clear() {
        logs.clear()
    }

    /**
     * Get only failed requests.
     */
    fun getFailedRequests(): List<NetworkLogEntry> {
        return logs.filter { !it.isSuccess }
    }

    /**
     * Get requests matching a URL pattern.
     */
    fun getLogsByUrlPattern(pattern: String): List<NetworkLogEntry> {
        val regex = Regex(pattern)
        return logs.filter { regex.containsMatchIn(it.url) }
    }

    /**
     * Get requests from the last N milliseconds.
     */
    fun getLogsFromLast(durationMs: Long): List<NetworkLogEntry> {
        val cutoff = System.currentTimeMillis() - durationMs
        return logs.filter { it.timestamp >= cutoff }
    }
}
