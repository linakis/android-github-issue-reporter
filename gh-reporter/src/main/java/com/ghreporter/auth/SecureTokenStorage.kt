package com.ghreporter.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Securely stores authentication tokens using EncryptedSharedPreferences.
 *
 * Uses AES256 encryption for both keys and values.
 */
class SecureTokenStorage(context: Context) {

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Store the GitHub access token securely.
     *
     * @param token The access token to store
     */
    fun saveGitHubToken(token: String) {
        sharedPreferences.edit()
            .putString(KEY_GITHUB_TOKEN, token)
            .putLong(KEY_TOKEN_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    /**
     * Retrieve the stored GitHub access token.
     *
     * @return The access token, or null if not stored
     */
    fun getGitHubToken(): String? {
        return sharedPreferences.getString(KEY_GITHUB_TOKEN, null)
    }

    /**
     * Check if a GitHub token is stored.
     *
     * @return true if a token exists
     */
    fun hasGitHubToken(): Boolean {
        return getGitHubToken() != null
    }

    /**
     * Get when the token was saved.
     *
     * @return Timestamp in milliseconds, or 0 if not set
     */
    fun getTokenSavedAt(): Long {
        return sharedPreferences.getLong(KEY_TOKEN_SAVED_AT, 0)
    }

    /**
     * Store the authenticated user's GitHub username.
     *
     * @param username The GitHub username
     */
    fun saveGitHubUsername(username: String) {
        sharedPreferences.edit()
            .putString(KEY_GITHUB_USERNAME, username)
            .apply()
    }

    /**
     * Retrieve the stored GitHub username.
     *
     * @return The username, or null if not stored
     */
    fun getGitHubUsername(): String? {
        return sharedPreferences.getString(KEY_GITHUB_USERNAME, null)
    }

    /**
     * Store the authenticated user's email.
     *
     * @param email The user's email
     */
    fun saveUserEmail(email: String) {
        sharedPreferences.edit()
            .putString(KEY_USER_EMAIL, email)
            .apply()
    }

    /**
     * Retrieve the stored user email.
     *
     * @return The email, or null if not stored
     */
    fun getUserEmail(): String? {
        return sharedPreferences.getString(KEY_USER_EMAIL, null)
    }

    /**
     * Store the authenticated user's avatar URL.
     *
     * @param avatarUrl The avatar URL
     */
    fun saveAvatarUrl(avatarUrl: String) {
        sharedPreferences.edit()
            .putString(KEY_AVATAR_URL, avatarUrl)
            .apply()
    }

    /**
     * Retrieve the stored avatar URL.
     *
     * @return The avatar URL, or null if not stored
     */
    fun getAvatarUrl(): String? {
        return sharedPreferences.getString(KEY_AVATAR_URL, null)
    }

    /**
     * Clear the GitHub token and all user data.
     */
    fun clearGitHubToken() {
        sharedPreferences.edit()
            .remove(KEY_GITHUB_TOKEN)
            .remove(KEY_TOKEN_SAVED_AT)
            .remove(KEY_GITHUB_USERNAME)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_AVATAR_URL)
            .apply()
    }

    /**
     * Clear all stored data.
     */
    fun clearAll() {
        sharedPreferences.edit()
            .clear()
            .apply()
    }

    /**
     * Get stored user info as a data class.
     */
    fun getUserInfo(): UserInfo? {
        val token = getGitHubToken() ?: return null
        return UserInfo(
            token = token,
            username = getGitHubUsername(),
            email = getUserEmail(),
            avatarUrl = getAvatarUrl(),
            savedAt = getTokenSavedAt()
        )
    }

    /**
     * Save user info from a data class.
     */
    fun saveUserInfo(userInfo: UserInfo) {
        val editor = sharedPreferences.edit()
            .putString(KEY_GITHUB_TOKEN, userInfo.token)
            .putLong(KEY_TOKEN_SAVED_AT, System.currentTimeMillis())

        userInfo.username?.let { editor.putString(KEY_GITHUB_USERNAME, it) }
        userInfo.email?.let { editor.putString(KEY_USER_EMAIL, it) }
        userInfo.avatarUrl?.let { editor.putString(KEY_AVATAR_URL, it) }

        editor.apply()
    }

    /**
     * Data class representing stored user information.
     */
    data class UserInfo(
        val token: String,
        val username: String?,
        val email: String?,
        val avatarUrl: String?,
        val savedAt: Long
    )

    companion object {
        private const val PREFS_FILE_NAME = "gh_reporter_secure_prefs"
        private const val KEY_GITHUB_TOKEN = "github_access_token"
        private const val KEY_TOKEN_SAVED_AT = "token_saved_at"
        private const val KEY_GITHUB_USERNAME = "github_username"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_AVATAR_URL = "avatar_url"

        @Volatile
        private var instance: SecureTokenStorage? = null

        /**
         * Get singleton instance.
         */
        fun getInstance(context: Context): SecureTokenStorage {
            return instance ?: synchronized(this) {
                instance ?: SecureTokenStorage(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
