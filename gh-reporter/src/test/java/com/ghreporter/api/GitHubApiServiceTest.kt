package com.ghreporter.api

import com.ghreporter.api.models.CreateGistRequest
import com.ghreporter.api.models.CreateIssueRequest
import com.ghreporter.api.models.GistFile
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class GitHubApiServiceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: GitHubApiService

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .writeTimeout(1, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        apiService = retrofit.create(GitHubApiService::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getAuthenticatedUser parses response correctly`() = runBlocking {
        // Given
        val jsonResponse = """
            {
                "id": 12345,
                "login": "testuser",
                "avatar_url": "https://avatars.githubusercontent.com/u/12345",
                "html_url": "https://github.com/testuser",
                "name": "Test User",
                "email": "test@example.com"
            }
        """.trimIndent()
        
        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        // When
        val response = apiService.getAuthenticatedUser()

        // Then
        assertTrue(response.isSuccessful)
        val user = response.body()!!
        assertEquals(12345L, user.id)
        assertEquals("testuser", user.login)
        assertEquals("Test User", user.name)
    }

    @Test
    fun `createGist sends correct request`() = runBlocking {
        // Given
        val jsonResponse = """
            {
                "id": "abc123",
                "html_url": "https://gist.github.com/abc123",
                "description": "Test gist",
                "public": false,
                "files": {
                    "test.txt": {
                        "filename": "test.txt",
                        "type": "text/plain",
                        "language": "Text",
                        "raw_url": "https://gist.githubusercontent.com/raw/test.txt",
                        "size": 100
                    }
                },
                "created_at": "2024-01-01T00:00:00Z",
                "updated_at": "2024-01-01T00:00:00Z",
                "owner": {
                    "id": 12345,
                    "login": "testuser",
                    "avatar_url": "https://avatars.githubusercontent.com/u/12345",
                    "html_url": "https://github.com/testuser"
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(201))

        val request = CreateGistRequest(
            description = "Test gist",
            public = false,
            files = mapOf("test.txt" to GistFile("Test content"))
        )

        // When
        val response = apiService.createGist(request)

        // Then
        assertTrue(response.isSuccessful)
        val gist = response.body()!!
        assertEquals("abc123", gist.id)
        assertEquals("https://gist.github.com/abc123", gist.htmlUrl)
        assertFalse(gist.public)

        // Verify request
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/gists", recordedRequest.path)
        assertTrue(recordedRequest.body.readUtf8().contains("Test content"))
    }

    @Test
    fun `createIssue sends correct request`() = runBlocking {
        // Given
        val jsonResponse = """
            {
                "id": 1,
                "number": 42,
                "title": "Bug report",
                "body": "Something is broken",
                "state": "open",
                "html_url": "https://github.com/owner/repo/issues/42",
                "user": {
                    "id": 12345,
                    "login": "testuser",
                    "avatar_url": "https://avatars.githubusercontent.com/u/12345",
                    "html_url": "https://github.com/testuser"
                },
                "labels": [
                    {
                        "id": 1,
                        "name": "bug",
                        "color": "d73a4a",
                        "description": "Something isn't working"
                    }
                ],
                "created_at": "2024-01-01T00:00:00Z",
                "updated_at": "2024-01-01T00:00:00Z"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(201))

        val request = CreateIssueRequest(
            title = "Bug report",
            body = "Something is broken",
            labels = listOf("bug")
        )

        // When
        val response = apiService.createIssue("owner", "repo", request)

        // Then
        assertTrue(response.isSuccessful)
        val issue = response.body()!!
        assertEquals(42, issue.number)
        assertEquals("Bug report", issue.title)
        assertEquals("https://github.com/owner/repo/issues/42", issue.htmlUrl)

        // Verify request
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/repos/owner/repo/issues", recordedRequest.path)
    }

    @Test
    fun `checkRepositoryAccess returns success for accessible repo`() = runBlocking {
        // Given
        val jsonResponse = """
            {
                "id": 1,
                "name": "repo",
                "full_name": "owner/repo",
                "private": false,
                "html_url": "https://github.com/owner/repo",
                "description": "Test repo",
                "owner": {
                    "id": 12345,
                    "login": "owner",
                    "avatar_url": "https://avatars.githubusercontent.com/u/12345",
                    "html_url": "https://github.com/owner"
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        // When
        val response = apiService.checkRepositoryAccess("owner", "repo")

        // Then
        assertTrue(response.isSuccessful)
        val repo = response.body()!!
        assertEquals("owner/repo", repo.fullName)
    }

    @Test
    fun `API returns 401 for unauthorized request`() = runBlocking {
        // Given
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Bad credentials"}"""))

        // When
        val response = apiService.getAuthenticatedUser()

        // Then
        assertFalse(response.isSuccessful)
        assertEquals(401, response.code())
    }

    @Test
    fun `API returns 404 for non-existent resource`() = runBlocking {
        // Given
        mockWebServer.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""))

        // When
        val response = apiService.checkRepositoryAccess("nonexistent", "repo")

        // Then
        assertFalse(response.isSuccessful)
        assertEquals(404, response.code())
    }
}
