package com.ghreporter.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ghreporter.GHReporter
import com.ghreporter.sample.ui.theme.GHReporterSampleTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Request
import timber.log.Timber

/**
 * Sample activity demonstrating GHReporter SDK features.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GHReporterSampleTheme {
                MainScreen(
                    onReportIssue = {
                        GHReporter.startReporting(this)
                    },
                    onGenerateLogs = { count ->
                        generateSampleLogs(count)
                    },
                    onMakeNetworkRequest = {
                        makeNetworkRequest()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Enable shake-to-report when activity is visible
        GHReporter.enableShakeToReport(this)
    }

    override fun onPause() {
        super.onPause()
        // Disable shake detection when activity is not visible
        GHReporter.disableShakeToReport()
    }

    private fun generateSampleLogs(count: Int) {
        repeat(count) { i ->
            when (i % 5) {
                0 -> Timber.v("Verbose log entry #$i")
                1 -> Timber.d("Debug log entry #$i - checking value: ${System.currentTimeMillis()}")
                2 -> Timber.i("Info log entry #$i - user action performed")
                3 -> Timber.w("Warning log entry #$i - potential issue detected")
                4 -> Timber.e("Error log entry #$i - something went wrong")
            }
        }
        Timber.i("Generated $count sample log entries")
    }

    private fun makeNetworkRequest() {
        val app = application as SampleApp
        val request = Request.Builder()
            .url("https://api.github.com/zen")
            .build()

        Thread {
            try {
                val response = app.okHttpClient.newCall(request).execute()
                val body = response.body?.string()
                Timber.i("Network response: $body")
            } catch (e: Exception) {
                Timber.e(e, "Network request failed")
            }
        }.start()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onReportIssue: () -> Unit,
    onGenerateLogs: (Int) -> Unit,
    onMakeNetworkRequest: () -> Unit
) {
    var logCount by remember { mutableIntStateOf(10) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GHReporter Sample") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Instructions card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Shake to Report!",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Shake your device to open the issue reporter, or use the button below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Report Issue button
            Button(
                onClick = onReportIssue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Report an Issue")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Generate logs section
            Text(
                text = "Test Log Collection",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedButton(
                onClick = { onGenerateLogs(logCount) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Generate $logCount Log Entries")
            }

            // Network request button
            OutlinedButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        onMakeNetworkRequest()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Make Network Request")
            }

            Spacer(modifier = Modifier.weight(1f))

            // Footer
            Text(
                text = "Logs and network requests will be captured by GHReporter and can be included in issue reports.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
