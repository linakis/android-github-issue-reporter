package com.ghreporter.ui

import android.net.Uri
import android.os.Bundle
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
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

        setContent {
            GHReporterTheme {
                GHReporterScreen(
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@Composable
fun GHReporterScreen(
    onDismiss: () -> Unit,
    viewModel: ReporterViewModel = viewModel(factory = ReporterViewModel.factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
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
