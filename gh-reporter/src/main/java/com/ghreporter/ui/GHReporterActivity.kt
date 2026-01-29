package com.ghreporter.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ghreporter.ui.screens.IssueFormScreen
import com.ghreporter.ui.screens.LoginScreen
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

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        viewModel.setScreenshotUri(uri)
    }

    AnimatedContent(
        targetState = when {
            uiState.submissionSuccess -> ScreenState.Success
            uiState.isAuthenticated -> ScreenState.Form
            else -> ScreenState.Login
        },
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "screen_transition"
    ) { screenState ->
        when (screenState) {
            ScreenState.Login -> {
                LoginScreen(
                    isLoading = uiState.isAuthLoading,
                    userCode = uiState.authUserCode,
                    verificationUri = uiState.authVerificationUri,
                    errorMessage = uiState.authError,
                    onSignInClick = { viewModel.signIn(context) },
                    onOpenBrowserClick = { viewModel.openVerificationUrl(context) },
                    onDismiss = onDismiss
                )
            }

            ScreenState.Form -> {
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
            }

            ScreenState.Success -> {
                SuccessScreen(
                    issueUrl = uiState.createdIssueUrl,
                    onOpenIssue = {
                        uiState.createdIssueUrl?.let { url ->
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                Uri.parse(url)
                            )
                            context.startActivity(intent)
                        }
                    },
                    onDismiss = onDismiss
                )
            }
        }
    }
}

private enum class ScreenState {
    Login,
    Form,
    Success
}

@Composable
private fun SuccessScreen(
    issueUrl: String?,
    onOpenIssue: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Issue Created!",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your issue has been successfully submitted to GitHub.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (issueUrl != null) {
                Button(
                    onClick = onOpenIssue,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = null
                    )
                    Text(
                        text = "View Issue on GitHub",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done")
            }
        }
    }
}
