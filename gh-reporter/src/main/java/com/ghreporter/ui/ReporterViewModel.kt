package com.ghreporter.ui

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ghreporter.GHReporter
import com.ghreporter.api.GistService
import com.ghreporter.api.GitHubApiClient
import com.ghreporter.api.IssueService
import com.ghreporter.auth.GitHubAuthManager
import com.ghreporter.auth.SecureTokenStorage
import com.ghreporter.collectors.GHReporterInterceptor
import com.ghreporter.collectors.GHReporterTree
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel for the GHReporter issue submission flow.
 */
class ReporterViewModel(
    private val authManager: GitHubAuthManager,
    private val gistService: GistService,
    private val issueService: IssueService,
    private val context: Context
) : ViewModel() {

    data class UiState(
        // Auth state
        val isAuthenticated: Boolean = false,
        val isAuthLoading: Boolean = false,
        val authUserCode: String? = null,
        val authVerificationUri: String? = null,
        val authError: String? = null,
        val username: String? = null,
        val avatarUrl: String? = null,

        // Form state
        val title: String = "",
        val body: String = "",
        val selectedLabels: Set<String> = emptySet(),
        val availableLabels: List<String> = emptyList(),

        // Toggle options
        val includeTimberLogs: Boolean = true,
        val includeNetworkLogs: Boolean = true,
        val includeLogcat: Boolean = true,
        val includeDeviceInfo: Boolean = true,
        val includeScreenshot: Boolean = false,

        // Screenshot
        val screenshotUri: Uri? = null,

        // Submission state
        val isSubmitting: Boolean = false,
        val submissionError: String? = null,
        val submissionSuccess: Boolean = false,
        val createdIssueUrl: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // Observe auth state
        viewModelScope.launch {
            authManager.observeAuthState().collect { authState ->
                when (authState) {
                    is GitHubAuthManager.AuthState.Authenticated -> {
                        _uiState.update {
                            it.copy(
                                isAuthenticated = true,
                                isAuthLoading = false,
                                authUserCode = null,
                                authVerificationUri = null,
                                authError = null,
                                username = authState.username,
                                avatarUrl = authState.avatarUrl
                            )
                        }
                    }
                    is GitHubAuthManager.AuthState.WaitingForUserCode -> {
                        _uiState.update {
                            it.copy(
                                isAuthenticated = false,
                                isAuthLoading = true,
                                authUserCode = authState.userCode,
                                authVerificationUri = authState.verificationUri,
                                authError = null
                            )
                        }
                    }
                    is GitHubAuthManager.AuthState.Loading -> {
                        _uiState.update {
                            it.copy(
                                isAuthLoading = true,
                                authError = null
                            )
                        }
                    }
                    is GitHubAuthManager.AuthState.Error -> {
                        _uiState.update {
                            it.copy(
                                isAuthLoading = false,
                                authError = authState.message
                            )
                        }
                    }
                    GitHubAuthManager.AuthState.NotAuthenticated -> {
                        _uiState.update {
                            it.copy(
                                isAuthenticated = false,
                                isAuthLoading = false,
                                authUserCode = null,
                                authVerificationUri = null,
                                username = null,
                                avatarUrl = null
                            )
                        }
                    }
                }
            }
        }

        // Load default labels from config
        _uiState.update {
            it.copy(
                availableLabels = GHReporter.getConfig().defaultLabels,
                selectedLabels = GHReporter.getConfig().defaultLabels.toSet()
            )
        }
    }

    fun signIn(context: Context) {
        viewModelScope.launch {
            authManager.signInWithGitHub(context, openBrowser = true)
        }
    }

    fun openVerificationUrl(context: Context) {
        val uri = _uiState.value.authVerificationUri ?: return
        authManager.openVerificationUrl(context, uri)
    }

    fun signOut() {
        authManager.signOut()
    }

    fun updateTitle(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun updateBody(body: String) {
        _uiState.update { it.copy(body = body) }
    }

    fun toggleLabel(label: String) {
        _uiState.update { state ->
            val newLabels = if (label in state.selectedLabels) {
                state.selectedLabels - label
            } else {
                state.selectedLabels + label
            }
            state.copy(selectedLabels = newLabels)
        }
    }

    fun setIncludeTimberLogs(include: Boolean) {
        _uiState.update { it.copy(includeTimberLogs = include) }
    }

    fun setIncludeNetworkLogs(include: Boolean) {
        _uiState.update { it.copy(includeNetworkLogs = include) }
    }

    fun setIncludeLogcat(include: Boolean) {
        _uiState.update { it.copy(includeLogcat = include) }
    }

    fun setIncludeDeviceInfo(include: Boolean) {
        _uiState.update { it.copy(includeDeviceInfo = include) }
    }

    fun setIncludeScreenshot(include: Boolean) {
        _uiState.update { it.copy(includeScreenshot = include) }
    }

    fun setScreenshotUri(uri: Uri?) {
        _uiState.update { it.copy(screenshotUri = uri) }
    }

    fun removeScreenshot() {
        _uiState.update { it.copy(screenshotUri = null) }
    }

    fun submitIssue() {
        val state = _uiState.value
        if (state.title.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, submissionError = null) }

            try {
                // Collect logs
                val logsContent = buildLogsContent(state)

                // Create gist with logs if we have any
                var gistUrl: String? = null
                if (logsContent.isNotBlank()) {
                    val gistResult = gistService.createGist(
                        description = "Logs for: ${state.title}",
                        filename = "issue-logs.md",
                        content = logsContent,
                        isPublic = false
                    )
                    gistUrl = gistResult.getOrNull()?.htmlUrl
                }

                // Build issue body
                val issueBody = buildIssueBody(state, gistUrl)

                // Create issue
                val config = GHReporter.getConfig()
                val issueResult = issueService.createIssue(
                    owner = config.githubOwner,
                    repo = config.githubRepo,
                    title = state.title,
                    body = issueBody,
                    labels = state.selectedLabels.toList()
                )

                issueResult.fold(
                    onSuccess = { issue ->
                        _uiState.update {
                            it.copy(
                                isSubmitting = false,
                                submissionSuccess = true,
                                createdIssueUrl = issue.htmlUrl
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isSubmitting = false,
                                submissionError = error.message ?: "Failed to create issue"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        submissionError = e.message ?: "Failed to submit issue"
                    )
                }
            }
        }
    }

    private suspend fun buildLogsContent(state: UiState): String {
        val sections = mutableListOf<String>()

        if (state.includeTimberLogs) {
            val timberLogs = GHReporter.getTimberLogs()
            if (timberLogs.isNotEmpty()) {
                sections.add(formatTimberLogs(timberLogs))
            }
        }

        if (state.includeNetworkLogs) {
            val networkLogs = GHReporter.getNetworkLogs()
            if (networkLogs.isNotEmpty()) {
                sections.add(formatNetworkLogs(networkLogs))
            }
        }

        if (state.includeLogcat) {
            val logcat = GHReporter.collectLogcat()
            if (logcat.isNotBlank()) {
                sections.add("## Logcat\n```\n$logcat\n```")
            }
        }

        return sections.joinToString("\n\n---\n\n")
    }

    private fun formatTimberLogs(logs: List<GHReporterTree.LogEntry>): String {
        val sb = StringBuilder()
        sb.appendLine("## App Logs (Timber)")
        sb.appendLine("```")
        logs.forEach { entry ->
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(entry.timestamp))
            sb.appendLine("[$time] ${entry.level}/${entry.tag}: ${entry.message}")
            entry.throwable?.let { sb.appendLine(it) }
        }
        sb.appendLine("```")
        return sb.toString()
    }

    private fun formatNetworkLogs(logs: List<GHReporterInterceptor.NetworkLogEntry>): String {
        val sb = StringBuilder()
        sb.appendLine("## Network Logs")
        logs.forEach { entry ->
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(entry.timestamp))
            sb.appendLine("\n### [$time] ${entry.method} ${entry.url}")
            sb.appendLine("**Status:** ${entry.responseCode} (${entry.durationMs}ms)")
            if (entry.requestHeaders.isNotEmpty()) {
                sb.appendLine("\n<details><summary>Request Headers</summary>\n")
                sb.appendLine("```")
                entry.requestHeaders.forEach { (k, v) -> sb.appendLine("$k: $v") }
                sb.appendLine("```")
                sb.appendLine("</details>")
            }
            entry.requestBody?.let { body ->
                sb.appendLine("\n<details><summary>Request Body</summary>\n")
                sb.appendLine("```json")
                sb.appendLine(body.take(10000))
                sb.appendLine("```")
                sb.appendLine("</details>")
            }
            entry.responseBody?.let { body ->
                sb.appendLine("\n<details><summary>Response Body</summary>\n")
                sb.appendLine("```json")
                sb.appendLine(body.take(10000))
                sb.appendLine("```")
                sb.appendLine("</details>")
            }
        }
        return sb.toString()
    }

    private fun buildIssueBody(state: UiState, gistUrl: String?): String {
        val sb = StringBuilder()

        // User description
        if (state.body.isNotBlank()) {
            sb.appendLine(state.body)
            sb.appendLine()
        }

        // Device info
        if (state.includeDeviceInfo) {
            sb.appendLine("## Device Information")
            sb.appendLine("| Property | Value |")
            sb.appendLine("|----------|-------|")
            sb.appendLine("| Device | ${Build.MANUFACTURER} ${Build.MODEL} |")
            sb.appendLine("| Android | ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) |")
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                sb.appendLine("| App Version | ${packageInfo.versionName} (${packageInfo.longVersionCode}) |")
            } catch (e: Exception) {
                // Ignore
            }
            sb.appendLine()
        }

        // Logs gist link
        if (gistUrl != null) {
            sb.appendLine("## Logs")
            sb.appendLine("[View detailed logs]($gistUrl)")
            sb.appendLine()
        }

        // Footer
        sb.appendLine("---")
        sb.appendLine("*Reported via GHReporter SDK*")

        return sb.toString()
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val appContext = context.applicationContext
                    val tokenStorage = SecureTokenStorage(appContext)
                    val authManager = GitHubAuthManager.getInstance(tokenStorage)
                    val apiClient = GitHubApiClient.getInstance { authManager.getGitHubToken() }
                    val gistService = GistService(apiClient.apiService)
                    val issueService = IssueService(apiClient.apiService)

                    return ReporterViewModel(
                        authManager = authManager,
                        gistService = gistService,
                        issueService = issueService,
                        context = appContext
                    ) as T
                }
            }
        }
    }
}
