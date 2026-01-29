package com.ghreporter.auth

import android.content.Context
import com.ghreporter.GHReporter
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Manages GitHub authentication using the Device Flow.
 *
 * Device Flow is ideal for mobile apps because:
 * - No client secret needed in the app
 * - No redirect URI handling required
 * - User enters a code at github.com/login/device
 *
 * Flow:
 * 1. App requests device code from GitHub
 * 2. User opens browser to github.com/login/device
 * 3. User enters the displayed code
 * 4. App polls GitHub until user completes auth
 * 5. GitHub returns access token
 */
class GitHubAuthManager(
    private val tokenStorage: SecureTokenStorage,
    private val clientId: String
) {

    private val httpClient = OkHttpClient.Builder().build()
    private val moshi = Moshi.Builder().build()

    private val _authState = MutableStateFlow<AuthState>(
        if (tokenStorage.hasGitHubToken()) {
            val userInfo = tokenStorage.getUserInfo()
            AuthState.Authenticated(
                username = userInfo?.username,
                email = userInfo?.email,
                avatarUrl = userInfo?.avatarUrl
            )
        } else {
            AuthState.NotAuthenticated
        }
    )

    /**
     * Result of starting the device flow.
     */
    @JsonClass(generateAdapter = true)
    data class DeviceCodeResponse(
        @Json(name = "device_code") val deviceCode: String,
        @Json(name = "user_code") val userCode: String,
        @Json(name = "verification_uri") val verificationUri: String,
        @Json(name = "expires_in") val expiresIn: Int,
        @Json(name = "interval") val interval: Int
    )

    /**
     * Token response from GitHub.
     */
    @JsonClass(generateAdapter = true)
    data class TokenResponse(
        @Json(name = "access_token") val accessToken: String? = null,
        @Json(name = "token_type") val tokenType: String? = null,
        @Json(name = "scope") val scope: String? = null,
        @Json(name = "error") val error: String? = null,
        @Json(name = "error_description") val errorDescription: String? = null
    )

    /**
     * GitHub user info.
     */
    @JsonClass(generateAdapter = true)
    data class GitHubUser(
        @Json(name = "login") val login: String,
        @Json(name = "email") val email: String? = null,
        @Json(name = "avatar_url") val avatarUrl: String? = null
    )

    /**
     * Result of an authentication attempt.
     */
    sealed class AuthResult {
        data class Success(
            val githubToken: String,
            val username: String?
        ) : AuthResult()

        data class Error(val message: String, val exception: Exception? = null) : AuthResult()
        object Cancelled : AuthResult()
    }

    /**
     * Authentication state.
     */
    sealed class AuthState {
        object NotAuthenticated : AuthState()
        object Loading : AuthState()
        data class WaitingForUserCode(
            val userCode: String,
            val verificationUri: String
        ) : AuthState()
        data class Authenticated(
            val username: String?,
            val email: String?,
            val avatarUrl: String?
        ) : AuthState()
        data class Error(val message: String) : AuthState()
    }

    /**
     * Check if user is currently authenticated with a valid token.
     */
    fun isAuthenticated(): Boolean {
        return tokenStorage.hasGitHubToken()
    }

    /**
     * Get the current GitHub access token.
     *
     * @return The access token or null if not authenticated
     */
    fun getGitHubToken(): String? {
        return tokenStorage.getGitHubToken()
    }

    /**
     * Get stored user information.
     */
    fun getUserInfo(): SecureTokenStorage.UserInfo? {
        return tokenStorage.getUserInfo()
    }

    /**
     * Observe authentication state as a Flow.
     */
    fun observeAuthState(): Flow<AuthState> = _authState.asStateFlow()

    /**
     * Start the GitHub Device Flow authentication.
     *
     * This will:
     * 1. Request a device code from GitHub
     * 2. Update state with the user code to display
     * 3. Poll until user completes authentication
     *
     * @return AuthResult indicating success, error, or cancellation
     */
    suspend fun signInWithGitHub(): AuthResult {
        _authState.value = AuthState.Loading

        // Step 1: Request device code
        val deviceCodeResult = requestDeviceCode()
        if (deviceCodeResult.isFailure) {
            val error = deviceCodeResult.exceptionOrNull()?.message ?: "Failed to get device code"
            _authState.value = AuthState.Error(error)
            return AuthResult.Error(error, deviceCodeResult.exceptionOrNull() as? Exception)
        }

        val deviceCode = deviceCodeResult.getOrThrow()

        // Step 2: Update state with user code for display
        _authState.value = AuthState.WaitingForUserCode(
            userCode = deviceCode.userCode,
            verificationUri = deviceCode.verificationUri
        )

        // Step 3: Poll for access token
        val pollResult = pollForAccessToken(
            deviceCode = deviceCode.deviceCode,
            interval = deviceCode.interval,
            expiresIn = deviceCode.expiresIn
        )

        return when {
            pollResult.isSuccess -> {
                val token = pollResult.getOrThrow()
                
                // Fetch user info
                val userInfo = fetchUserInfo(token)
                
                // Store token and user info
                tokenStorage.saveUserInfo(
                    SecureTokenStorage.UserInfo(
                        token = token,
                        username = userInfo?.login,
                        email = userInfo?.email,
                        avatarUrl = userInfo?.avatarUrl,
                        savedAt = System.currentTimeMillis()
                    )
                )

                _authState.value = AuthState.Authenticated(
                    username = userInfo?.login,
                    email = userInfo?.email,
                    avatarUrl = userInfo?.avatarUrl
                )

                AuthResult.Success(
                    githubToken = token,
                    username = userInfo?.login
                )
            }
            pollResult.exceptionOrNull()?.message?.contains("expired") == true -> {
                _authState.value = AuthState.NotAuthenticated
                AuthResult.Cancelled
            }
            else -> {
                val error = pollResult.exceptionOrNull()?.message ?: "Authentication failed"
                _authState.value = AuthState.Error(error)
                AuthResult.Error(error, pollResult.exceptionOrNull() as? Exception)
            }
        }
    }

    /**
     * Request a device code from GitHub.
     */
    private suspend fun requestDeviceCode(): Result<DeviceCodeResponse> = withContext(Dispatchers.IO) {
        val requestBody = FormBody.Builder()
            .add("client_id", clientId)
            .add("scope", "repo gist read:user user:email")
            .build()

        val request = Request.Builder()
            .url("https://github.com/login/device/code")
            .addHeader("Accept", "application/json")
            .post(requestBody)
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(IOException("Empty response"))

            if (!response.isSuccessful) {
                // Provide helpful error messages for common issues
                val errorMessage = when (response.code) {
                    400, 401, 403 -> {
                        "Invalid GitHub Client ID. Please check your GHReporterConfig.\n\n" +
                        "To fix this:\n" +
                        "1. Create a GitHub OAuth App at https://github.com/settings/developers\n" +
                        "2. Select 'New OAuth App' and enable Device Flow\n" +
                        "3. Copy the Client ID and update your GHReporterConfig\n\n" +
                        "Current Client ID: ${clientId.take(10)}..."
                    }
                    else -> "Failed to get device code: HTTP ${response.code}"
                }
                return@withContext Result.failure(IOException(errorMessage))
            }

            val adapter = moshi.adapter(DeviceCodeResponse::class.java)
            val deviceCode = adapter.fromJson(body)
                ?: return@withContext Result.failure(IOException("Failed to parse device code response"))

            Result.success(deviceCode)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Poll GitHub for the access token until the user completes authentication.
     */
    private suspend fun pollForAccessToken(
        deviceCode: String,
        interval: Int,
        expiresIn: Int
    ): Result<String> {
        val pollIntervalMs = (interval * 1000L).coerceAtLeast(5000L)
        val expiresAtMs = System.currentTimeMillis() + (expiresIn * 1000L)

        val requestBody = FormBody.Builder()
            .add("client_id", clientId)
            .add("device_code", deviceCode)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .build()

        val tokenAdapter = moshi.adapter(TokenResponse::class.java)

        while (System.currentTimeMillis() < expiresAtMs) {
            delay(pollIntervalMs)

            val request = Request.Builder()
                .url("https://github.com/login/oauth/access_token")
                .addHeader("Accept", "application/json")
                .post(requestBody)
                .build()

            try {
                val response = withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute()
                }
                val body = response.body?.string() ?: continue

                val tokenResponse = tokenAdapter.fromJson(body) ?: continue

                when (tokenResponse.error) {
                    null, "" -> {
                        // Success!
                        tokenResponse.accessToken?.let { token ->
                            return Result.success(token)
                        }
                    }
                    "authorization_pending" -> {
                        // User hasn't authorized yet, keep polling
                        continue
                    }
                    "slow_down" -> {
                        // We're polling too fast, add extra delay
                        delay(5000L)
                        continue
                    }
                    "expired_token" -> {
                        return Result.failure(IOException("Device code expired. Please try again."))
                    }
                    "access_denied" -> {
                        return Result.failure(IOException("Access denied by user."))
                    }
                    else -> {
                        return Result.failure(
                            IOException("${tokenResponse.error}: ${tokenResponse.errorDescription}")
                        )
                    }
                }
            } catch (e: Exception) {
                // Network error, keep trying
                continue
            }
        }

        return Result.failure(IOException("Authentication expired. Please try again."))
    }

    /**
     * Fetch user info from GitHub API.
     */
    private suspend fun fetchUserInfo(accessToken: String): GitHubUser? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/user")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Accept", "application/vnd.github+json")
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null

            if (!response.isSuccessful) return@withContext null

            val adapter = moshi.adapter(GitHubUser::class.java)
            adapter.fromJson(body)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Sign out and clear stored tokens.
     */
    fun signOut() {
        tokenStorage.clearGitHubToken()
        _authState.value = AuthState.NotAuthenticated
    }

    /**
     * Re-authenticate the user (clears current session and starts fresh).
     * Call this if API calls start failing with 401.
     *
     * @return AuthResult
     */
    suspend fun reAuthenticate(): AuthResult {
        signOut()
        return signInWithGitHub()
    }

    companion object {
        @Volatile
        private var instance: GitHubAuthManager? = null

        /**
         * Get singleton instance.
         */
        fun getInstance(tokenStorage: SecureTokenStorage): GitHubAuthManager {
            return instance ?: synchronized(this) {
                instance ?: GitHubAuthManager(
                    tokenStorage = tokenStorage,
                    clientId = GHReporter.getConfig().githubClientId
                ).also {
                    instance = it
                }
            }
        }

        /**
         * Clear the singleton instance (useful for testing).
         */
        internal fun clearInstance() {
            instance = null
        }
    }
}
