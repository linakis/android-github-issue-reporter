package com.ghreporter.collectors

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GHReporterInterceptorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var interceptor: GHReporterInterceptor
    private lateinit var client: OkHttpClient

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        interceptor = GHReporterInterceptor(maxEntries = 10)
        client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `intercept captures successful request`() {
        // Given
        mockWebServer.enqueue(MockResponse().setBody("""{"status":"ok"}""").setResponseCode(200))
        val request = Request.Builder()
            .url(mockWebServer.url("/api/test"))
            .build()

        // When
        val response = client.newCall(request).execute()

        // Then
        assertEquals(200, response.code)
        assertEquals(1, interceptor.size())
        
        val log = interceptor.getLogs().first()
        assertEquals("GET", log.method)
        assertTrue(log.url.endsWith("/api/test"))
        assertEquals(200, log.responseCode)
        assertTrue(log.isSuccess)
        assertNull(log.error)
    }

    @Test
    fun `intercept captures failed request`() {
        // Given
        mockWebServer.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))
        val request = Request.Builder()
            .url(mockWebServer.url("/api/missing"))
            .build()

        // When
        val response = client.newCall(request).execute()

        // Then
        assertEquals(404, response.code)
        assertEquals(1, interceptor.size())
        
        val log = interceptor.getLogs().first()
        assertEquals(404, log.responseCode)
        assertFalse(log.isSuccess)
    }

    @Test
    fun `intercept respects max entries limit`() {
        // Given - enqueue more responses than max entries
        repeat(15) {
            mockWebServer.enqueue(MockResponse().setBody("response $it").setResponseCode(200))
        }

        // When - make 15 requests
        repeat(15) {
            val request = Request.Builder()
                .url(mockWebServer.url("/api/request$it"))
                .build()
            client.newCall(request).execute()
        }

        // Then - should only have 10 entries (maxEntries)
        assertEquals(10, interceptor.size())
        
        // Should have the last 10 requests (5-14)
        val logs = interceptor.getLogs()
        assertTrue(logs.first().url.contains("request5"))
        assertTrue(logs.last().url.contains("request14"))
    }

    @Test
    fun `clear removes all logs`() {
        // Given
        mockWebServer.enqueue(MockResponse().setBody("test").setResponseCode(200))
        val request = Request.Builder()
            .url(mockWebServer.url("/api/test"))
            .build()
        client.newCall(request).execute()
        assertEquals(1, interceptor.size())

        // When
        interceptor.clear()

        // Then
        assertEquals(0, interceptor.size())
        assertTrue(interceptor.getLogs().isEmpty())
    }

    @Test
    fun `getFailedRequests returns only failures`() {
        // Given
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        mockWebServer.enqueue(MockResponse().setResponseCode(201))
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        repeat(4) {
            val request = Request.Builder()
                .url(mockWebServer.url("/api/test$it"))
                .build()
            client.newCall(request).execute()
        }

        // When
        val failures = interceptor.getFailedRequests()

        // Then
        assertEquals(2, failures.size)
        assertTrue(failures.all { !it.isSuccess })
        assertEquals(500, failures[0].responseCode)
        assertEquals(404, failures[1].responseCode)
    }

    @Test
    fun `getLogsByUrlPattern filters correctly`() {
        // Given
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        listOf("/api/users", "/api/posts", "/admin/users").forEach { path ->
            val request = Request.Builder()
                .url(mockWebServer.url(path))
                .build()
            client.newCall(request).execute()
        }

        // When
        val userLogs = interceptor.getLogsByUrlPattern(".*users.*")

        // Then
        assertEquals(2, userLogs.size)
    }

    @Test
    fun `formatSummary produces correct format`() {
        // Given
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        val request = Request.Builder()
            .url(mockWebServer.url("/api/test"))
            .build()
        client.newCall(request).execute()

        // When
        val log = interceptor.getLogs().first()
        val summary = log.formatSummary()

        // Then
        assertTrue(summary.contains("GET"))
        assertTrue(summary.contains("/api/test"))
        assertTrue(summary.contains("200"))
        assertTrue(summary.contains("ms"))
    }

    @Test
    fun `sensitive headers are redacted in formatDetailed`() {
        // Given
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        val request = Request.Builder()
            .url(mockWebServer.url("/api/test"))
            .header("Authorization", "Bearer secret-token")
            .header("X-Api-Key", "my-api-key")
            .header("Content-Type", "application/json")
            .build()
        client.newCall(request).execute()

        // When
        val log = interceptor.getLogs().first()
        val detailed = log.formatDetailed()

        // Then
        assertTrue(detailed.contains("[REDACTED]"))
        assertTrue(detailed.contains("application/json"))
        assertFalse(detailed.contains("secret-token"))
        assertFalse(detailed.contains("my-api-key"))
    }
}
