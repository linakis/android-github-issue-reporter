package com.ghreporter.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Issue form screen for composing and submitting GitHub issues.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IssueFormScreen(
    // User info
    username: String?,
    avatarUrl: String?,
    // Form fields
    title: String,
    body: String,
    selectedLabels: Set<String>,
    availableLabels: List<String>,
    // Toggle options
    includeTimberLogs: Boolean,
    includeNetworkLogs: Boolean,
    includeLogcat: Boolean,
    includeDeviceInfo: Boolean,
    includeScreenshot: Boolean,
    // Screenshot
    screenshotUri: Uri?,
    // State
    isSubmitting: Boolean,
    errorMessage: String?,
    // Callbacks
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onLabelToggle: (String) -> Unit,
    onIncludeTimberLogsChange: (Boolean) -> Unit,
    onIncludeNetworkLogsChange: (Boolean) -> Unit,
    onIncludeLogcatChange: (Boolean) -> Unit,
    onIncludeDeviceInfoChange: (Boolean) -> Unit,
    onIncludeScreenshotChange: (Boolean) -> Unit,
    onPickScreenshot: () -> Unit,
    onRemoveScreenshot: () -> Unit,
    onSubmit: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Report Issue") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                },
                actions = {
                    // User avatar & sign out
                    if (username != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = "User avatar",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = username,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = onSignOut) {
                                Icon(
                                    imageVector = Icons.Default.Logout,
                                    contentDescription = "Sign out",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Title field
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                label = { Text("Title") },
                placeholder = { Text("Brief description of the issue") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSubmitting
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Body field
            OutlinedTextField(
                value = body,
                onValueChange = onBodyChange,
                label = { Text("Description") },
                placeholder = { Text("Describe what happened, steps to reproduce, expected behavior...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                enabled = !isSubmitting
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Labels section
            if (availableLabels.isNotEmpty()) {
                Text(
                    text = "Labels",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableLabels.forEach { label ->
                        FilterChip(
                            selected = label in selectedLabels,
                            onClick = { onLabelToggle(label) },
                            label = { Text(label) },
                            enabled = !isSubmitting,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Screenshot section
            Text(
                text = "Screenshot",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = includeScreenshot,
                    onCheckedChange = onIncludeScreenshotChange,
                    enabled = !isSubmitting
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Include screenshot",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (includeScreenshot) {
                Spacer(modifier = Modifier.height(12.dp))

                if (screenshotUri != null) {
                    // Show selected screenshot
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        AsyncImage(
                            model = screenshotUri,
                            contentDescription = "Screenshot",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        // Remove button
                        IconButton(
                            onClick = onRemoveScreenshot,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove screenshot",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else {
                    // Pick screenshot button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable(enabled = !isSubmitting) { onPickScreenshot() },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap to select screenshot",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Logs section
            Text(
                text = "Include in report",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Timber logs toggle
            LogToggleRow(
                label = "App logs (Timber)",
                description = "In-app log messages",
                checked = includeTimberLogs,
                onCheckedChange = onIncludeTimberLogsChange,
                enabled = !isSubmitting
            )

            // Network logs toggle
            LogToggleRow(
                label = "Network logs",
                description = "HTTP requests and responses",
                checked = includeNetworkLogs,
                onCheckedChange = onIncludeNetworkLogsChange,
                enabled = !isSubmitting
            )

            // Logcat toggle
            LogToggleRow(
                label = "System logs (Logcat)",
                description = "Android system logs",
                checked = includeLogcat,
                onCheckedChange = onIncludeLogcatChange,
                enabled = !isSubmitting
            )

            // Device info toggle
            LogToggleRow(
                label = "Device information",
                description = "Device model, OS version, app version",
                checked = includeDeviceInfo,
                onCheckedChange = onIncludeDeviceInfoChange,
                enabled = !isSubmitting
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Error message
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Submit button
            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting && title.isNotBlank()
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Submitting...")
                } else {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Submit Issue")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LogToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
