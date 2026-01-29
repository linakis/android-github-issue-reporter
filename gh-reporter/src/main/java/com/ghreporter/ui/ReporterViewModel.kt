package com.ghreporter.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ghreporter.GHReporter
import com.ghreporter.api.GistService
import com.ghreporter.api.IssueService
import com.ghreporter.auth.GitHubAuthManager
import com.ghreporter.auth.SecureTokenStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the GHReporter issue submission flow.
 */
class ReporterViewModel(
    private val authManager: GitHubAuthManager,
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
                // Process screenshot if included
                val screenshotUris = if (state.includeScreenshot && state.screenshotUri != null) {
                    listOf(state.screenshotUri)
                } else {
                    emptyList()
                }

                // Build issue options
                val options = IssueService.IssueOptions(
                    title = state.title,
                    description = state.body,
                    includeTimberLogs = state.includeTimberLogs,
                    includeOkHttpLogs = state.includeNetworkLogs,
                    includeLogcat = state.includeLogcat,
                    includeDeviceInfo = state.includeDeviceInfo,
                    includeAppInfo = state.includeDeviceInfo,
                    screenshotUris = screenshotUris,
                    additionalLabels = state.selectedLabels.toList()
                )

                // Create issue using IssueService
                val config = GHReporter.getConfig()
                val result = issueService.createIssue(
                    context = context,
                    owner = config.githubOwner,
                    repo = config.githubRepo,
                    options = options
                )

                when (result) {
                    is IssueService.IssueResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isSubmitting = false,
                                submissionSuccess = true,
                                createdIssueUrl = result.issue.htmlUrl
                            )
                        }
                    }
                    is IssueService.IssueResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isSubmitting = false,
                                submissionError = result.message
                            )
                        }
                    }
                }
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

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val appContext = context.applicationContext
                    val tokenStorage = SecureTokenStorage(appContext)
                    val authManager = GitHubAuthManager.getInstance(tokenStorage)
                    val gistService = GistService.getInstance(tokenStorage)
                    val issueService = IssueService.getInstance(tokenStorage, gistService)

                    return ReporterViewModel(
                        authManager = authManager,
                        issueService = issueService,
                        context = appContext
                    ) as T
                }
            }
        }
    }
}
