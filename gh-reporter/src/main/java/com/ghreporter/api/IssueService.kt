package com.ghreporter.api

import android.content.Context
import android.net.Uri
import android.os.Build
import com.ghreporter.GHReporter
import com.ghreporter.api.models.CreateIssueRequest
import com.ghreporter.api.models.IssueResponse
import com.ghreporter.auth.SecureTokenStorage
import com.ghreporter.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Service for creating GitHub issues.
 *
 * Handles issue creation with optional log attachments via Gist.
 */
class IssueService(
    private val tokenStorage: SecureTokenStorage,
    private val gistService: GistService
) {

    private val apiService: GitHubApiService by lazy {
        GitHubApiClient.create(tokenStorage)
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)

    /**
     * Result of an issue creation attempt.
     */
    sealed class IssueResult {
        data class Success(
            val issue: IssueResponse,
            val gistUrl: String?
        ) : IssueResult()

        data class Error(
            val message: String,
            val exception: Exception? = null
        ) : IssueResult()
    }

    /**
     * Options for creating an issue.
     */
    data class IssueOptions(
        val title: String,
        val description: String,
        val includeTimberLogs: Boolean = true,
        val includeOkHttpLogs: Boolean = true,
        val includeLogcat: Boolean = true,
        val includeDeviceInfo: Boolean = true,
        val includeAppInfo: Boolean = true,
        val screenshotUris: List<Uri> = emptyList(),
        val additionalLabels: List<String> = emptyList()
    )

    /**
     * Create a GitHub issue with the provided options.
     *
     * @param context Android context for device/app info
     * @param owner Repository owner
     * @param repo Repository name
     * @param options Issue creation options
     * @return IssueResult indicating success or failure
     */
    suspend fun createIssue(
        context: Context,
        owner: String,
        repo: String,
        options: IssueOptions
    ): IssueResult = withContext(Dispatchers.IO) {
        try {
            var gistUrl: String? = null

            // Process screenshot if included
            var screenshotBase64: String? = null
            if (options.screenshotUris.isNotEmpty()) {
                val maxWidth = GHReporter.getConfig().screenshotMaxWidth
                val processedImage = ImageUtils.processImage(context, options.screenshotUris.first(), maxWidth)
                screenshotBase64 = processedImage?.base64
            }

            // Create Gist for logs if any log option is enabled OR screenshot is present
            if (options.includeTimberLogs || options.includeOkHttpLogs || options.includeLogcat || screenshotBase64 != null) {
                val gistResult = gistService.createLogsGistFromCollectors(
                    includeTimber = options.includeTimberLogs,
                    includeOkHttp = options.includeOkHttpLogs,
                    includeLogcat = options.includeLogcat,
                    screenshotBase64 = screenshotBase64,
                    issueTitle = options.title
                )

                when (gistResult) {
                    is GistService.GistResult.Success -> {
                        gistUrl = gistResult.gist.htmlUrl
                    }
                    is GistService.GistResult.Error -> {
                        // Log but don't fail - continue without Gist
                        // The user can still report the issue
                    }
                }
            }

            // Build issue body
            val body = buildIssueBody(
                context = context,
                description = options.description,
                gistUrl = gistUrl,
                includeDeviceInfo = options.includeDeviceInfo,
                includeAppInfo = options.includeAppInfo,
                screenshotUris = options.screenshotUris,
                hasGist = gistUrl != null
            )

            // Combine default labels with additional labels
            val config = GHReporter.getConfig()
            val allLabels = (config.defaultLabels + options.additionalLabels).distinct()

            val request = CreateIssueRequest(
                title = options.title,
                body = body,
                labels = allLabels.takeIf { it.isNotEmpty() }
            )

            val response = apiService.createIssue(owner, repo, request)

            if (response.isSuccessful) {
                val issue = response.body()
                if (issue != null) {
                    IssueResult.Success(issue, gistUrl)
                } else {
                    IssueResult.Error("Empty response from GitHub API")
                }
            } else {
                val errorBody = response.errorBody()?.string()
                IssueResult.Error(
                    "Failed to create issue: ${response.code()} - ${errorBody ?: response.message()}"
                )
            }
        } catch (e: Exception) {
            IssueResult.Error("Failed to create issue: ${e.message}", e)
        }
    }

    private fun buildIssueBody(
        context: Context,
        description: String,
        gistUrl: String?,
        includeDeviceInfo: Boolean,
        includeAppInfo: Boolean,
        screenshotUris: List<Uri>,
        hasGist: Boolean
    ): String = buildString {
        // User description
        appendLine("## Description")
        appendLine()
        appendLine(description)
        appendLine()

        // Screenshots note
        if (screenshotUris.isNotEmpty()) {
            appendLine("## Screenshot")
            appendLine()
            if (hasGist && gistUrl != null) {
                appendLine("A screenshot is included in the debug logs Gist.")
                appendLine()
                appendLine("**To view the screenshot:**")
                appendLine("1. Open the [Gist]($gistUrl#file-screenshot-html)")
                appendLine("2. Click on `screenshot.html` in the file list")
                appendLine("3. Click the **Download** button (download icon in the top-right)")
                appendLine("4. Open the downloaded HTML file in your web browser")
                appendLine()
            } else {
                appendLine("*${screenshotUris.size} screenshot(s) were selected but could not be uploaded.*")
                appendLine("*Please attach them manually if needed.*")
            }
            appendLine()
        }

        // Logs link
        if (gistUrl != null) {
            appendLine("## Logs")
            appendLine()
            appendLine("Debug logs are available in this private Gist:")
            appendLine("[$gistUrl]($gistUrl)")
            appendLine()
        }

        // Device info
        if (includeDeviceInfo) {
            appendLine("## Device Information")
            appendLine()
            appendLine("| Property | Value |")
            appendLine("|----------|-------|")
            appendLine("| Device | ${Build.MANUFACTURER} ${Build.MODEL} |")
            appendLine("| Android Version | ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) |")
            appendLine("| Build | ${Build.DISPLAY} |")
            appendLine("| Hardware | ${Build.HARDWARE} |")
            appendLine("| Product | ${Build.PRODUCT} |")
            appendLine()
        }

        // App info
        if (includeAppInfo) {
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }

                appendLine("## App Information")
                appendLine()
                appendLine("| Property | Value |")
                appendLine("|----------|-------|")
                appendLine("| Package | ${context.packageName} |")
                appendLine("| Version | ${packageInfo.versionName} ($versionCode) |")
                appendLine()
            } catch (e: Exception) {
                // Skip if we can't get package info
            }
        }

        // Footer
        appendLine("---")
        appendLine("*Reported via GHReporter SDK at ${dateFormat.format(Date())}*")
    }

    /**
     * Verify that the user has access to the target repository.
     *
     * @param owner Repository owner
     * @param repo Repository name
     * @return true if the user has access
     */
    suspend fun verifyRepositoryAccess(owner: String, repo: String): Boolean = 
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.checkRepositoryAccess(owner, repo)
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }

    /**
     * Get the authenticated user's username.
     */
    suspend fun getAuthenticatedUsername(): String? = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getAuthenticatedUser()
            if (response.isSuccessful) {
                response.body()?.login
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        @Volatile
        private var instance: IssueService? = null

        /**
         * Get singleton instance.
         */
        fun getInstance(
            tokenStorage: SecureTokenStorage,
            gistService: GistService
        ): IssueService {
            return instance ?: synchronized(this) {
                instance ?: IssueService(tokenStorage, gistService).also {
                    instance = it
                }
            }
        }
    }
}
