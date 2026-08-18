package com.ghreporter.ui

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ghreporter.ui.components.AuthDialog
import com.ghreporter.ui.screens.IssueFormScreen
import com.ghreporter.ui.theme.GHReporterTheme

/**
 * Main activity for the GHReporter issue reporting flow.
 *
 * Handles:
 * - Authentication (Device Flow)
 * - Issue form
 * - Screenshot selection
 * - Submission
 */
class GHReporterActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefillTitle = intent.getStringExtra(EXTRA_PREFILL_TITLE)
        val prefillBody = intent.getStringExtra(EXTRA_PREFILL_BODY)

        setContent {
            GHReporterTheme {
                GHReporterScreen(
                    onDismiss = { finish() },
                    prefillTitle = prefillTitle,
                    prefillBody = prefillBody,
                )
            }
        }
    }

    companion object {
        /**
         * Optional pre-filled title/body, used by [com.ghreporter.GHReporter] to route a
         * detected crash into the normal sign-in-then-review flow when the user isn't
         * authenticated yet (an authenticated user's crash reports skip this UI entirely and
         * submit silently — see GHReporter.checkForPendingCrash).
         */
        const val EXTRA_PREFILL_TITLE = "com.ghreporter.EXTRA_PREFILL_TITLE"
        const val EXTRA_PREFILL_BODY = "com.ghreporter.EXTRA_PREFILL_BODY"
    }
}

@Composable
fun GHReporterScreen(
    onDismiss: () -> Unit,
    prefillTitle: String? = null,
    prefillBody: String? = null,
    viewModel: ReporterViewModel =
        viewModel(factory = ReporterViewModel.factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Applied once on first composition, not on every recomposition — the user may go on to
    // edit the pre-filled text, and we shouldn't stomp their edits back to the crash defaults.
    LaunchedEffect(Unit) {
        if (prefillTitle != null) viewModel.updateTitle(prefillTitle)
        if (prefillBody != null) viewModel.updateBody(prefillBody)
    }

    // Observe toast messages (informational only, don't close)
    LaunchedEffect(Unit) {
        viewModel.toastMessages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Observe issue created event (show toast and close activity)
    LaunchedEffect(Unit) {
        viewModel.issueCreatedEvent.collect {
            Toast.makeText(context, "Issue created successfully", Toast.LENGTH_LONG).show()
            onDismiss()
        }
    }

    // Auto-trigger authentication when not authenticated
    LaunchedEffect(uiState.isAuthenticated) {
        if (!uiState.isAuthenticated && !uiState.isAuthLoading && uiState.authUserCode == null) {
            viewModel.signIn()
        }
    }

    // Photo picker launcher
    val photoPickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia()) {
            uri: Uri? ->
            viewModel.setScreenshotUri(uri)
        }

    Box(modifier = Modifier.fillMaxSize()) {
        // Always show the issue form
        IssueFormScreen(
            username = uiState.username,
            avatarUrl = uiState.avatarUrl,
            title = uiState.title,
            body = uiState.body,
            selectedLabels = uiState.selectedLabels,
            availableLabels = uiState.availableLabels,
            includeTimberLogs = uiState.includeTimberLogs,
            includeNetworkLogs = uiState.includeNetworkLogs,
            includeLogcat = uiState.includeLogcat,
            includeDeviceInfo = uiState.includeDeviceInfo,
            includeScreenshot = uiState.includeScreenshot,
            screenshotUri = uiState.screenshotUri,
            isSubmitting = uiState.isSubmitting,
            errorMessage = uiState.submissionError,
            onTitleChange = viewModel::updateTitle,
            onBodyChange = viewModel::updateBody,
            onLabelToggle = viewModel::toggleLabel,
            onIncludeTimberLogsChange = viewModel::setIncludeTimberLogs,
            onIncludeNetworkLogsChange = viewModel::setIncludeNetworkLogs,
            onIncludeLogcatChange = viewModel::setIncludeLogcat,
            onIncludeDeviceInfoChange = viewModel::setIncludeDeviceInfo,
            onIncludeScreenshotChange = viewModel::setIncludeScreenshot,
            onPickScreenshot = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onRemoveScreenshot = viewModel::removeScreenshot,
            onSubmit = viewModel::submitIssue,
            onSignOut = viewModel::signOut,
            onDismiss = onDismiss
        )

        // Show auth dialog overlay when not authenticated
        if (!uiState.isAuthenticated) {
            AuthDialog(
                isLoading = uiState.isAuthLoading,
                userCode = uiState.authUserCode,
                verificationUri = uiState.authVerificationUri,
                errorMessage = uiState.authError,
                onCancel = onDismiss
            )
        }
    }
}
