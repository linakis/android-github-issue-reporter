package com.ghreporter.crash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.ghreporter.ui.GHReporterActivity
import com.ghreporter.ui.theme.GHReporterTheme

/**
 * Shown once, on the first launch after an unauthenticated user's app crashed — asks whether
 * to report it, since the crash can't have been auto-submitted without a signed-in GitHub
 * account. Accepting hands the pre-formatted title/body to [GHReporterActivity]'s normal
 * sign-in-then-review flow; declining just closes, and the crash is not asked about again
 * (the pending report was already cleared by [CrashReporter] before this activity was
 * launched).
 */
internal class CrashPromptActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefillTitle = intent.getStringExtra(GHReporterActivity.EXTRA_PREFILL_TITLE)
        val prefillBody = intent.getStringExtra(GHReporterActivity.EXTRA_PREFILL_BODY)

        setContent {
            GHReporterTheme {
                CrashPromptDialog(
                    onReport = {
                        startActivity(
                            Intent(this, GHReporterActivity::class.java).apply {
                                putExtra(GHReporterActivity.EXTRA_PREFILL_TITLE, prefillTitle)
                                putExtra(GHReporterActivity.EXTRA_PREFILL_BODY, prefillBody)
                            },
                        )
                        finish()
                    },
                    onDismiss = { finish() },
                )
            }
        }
    }
}

@Composable
private fun CrashPromptDialog(onReport: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("The app crashed") },
        text = { Text("Would you like to report what happened? You'll be able to review the details before sending.") },
        confirmButton = { TextButton(onClick = onReport) { Text("Report") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}
