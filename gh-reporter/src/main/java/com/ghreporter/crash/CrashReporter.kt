package com.ghreporter.crash

import android.content.Context
import android.content.Intent
import android.util.Log
import com.ghreporter.GHReporter
import com.ghreporter.GHReporterConfig
import com.ghreporter.api.GistService
import com.ghreporter.api.IssueService
import com.ghreporter.auth.SecureTokenStorage
import com.ghreporter.collectors.GHReporterTree
import com.ghreporter.ui.GHReporterActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Owns the crash-capture lifecycle: installing [CrashHandler] at init, and — on the next
 * launch after a crash — either silently filing the GitHub issue (user already authenticated)
 * or routing into the normal sign-in-then-review UI (see [GHReporterActivity]) so the crash
 * isn't lost just because no one was signed in yet.
 */
internal object CrashReporter {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Installs [CrashHandler] as the thread's uncaught-exception handler, chaining the previous one. */
    fun install(
        context: Context,
        appPackage: String,
        appVersionName: String?,
        appVersionCode: Long,
        timberTree: GHReporterTree?,
    ) {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(
            CrashHandler(
                context = context.applicationContext,
                appPackage = appPackage,
                appVersionName = appVersionName,
                appVersionCode = appVersionCode,
                timberTree = timberTree,
                previousHandler = previousHandler,
            ),
        )
    }

    /**
     * Call once per process start (from [GHReporter.init]) to pick up and file a crash from
     * the *previous* run, if any. No-op if nothing is pending.
     */
    fun checkForPendingCrash(context: Context, config: GHReporterConfig) {
        val appContext = context.applicationContext
        val storage = PendingCrashStorage(appContext)
        val report = storage.load() ?: return

        val tokenStorage = SecureTokenStorage.getInstance(appContext)
        if (tokenStorage.hasGitHubToken()) {
            submitSilently(appContext, config, report, storage)
        } else {
            promptToReport(appContext, report, storage)
        }
    }

    private fun submitSilently(
        context: Context,
        config: GHReporterConfig,
        report: PendingCrashReport,
        storage: PendingCrashStorage,
    ) {
        scope.launch {
            try {
                val tokenStorage = SecureTokenStorage.getInstance(context)
                val gistService = GistService.getInstance(tokenStorage)
                val issueService = IssueService.getInstance(tokenStorage, gistService)

                val result =
                    issueService.createRawIssue(
                        owner = config.githubOwner,
                        repo = config.githubRepo,
                        title = CrashReportFormatter.title(report),
                        body = CrashReportFormatter.body(report),
                        labels = listOf("crash"),
                    )

                when (result) {
                    is IssueService.IssueResult.Success ->
                        Log.i(TAG, "Crash reported automatically: ${result.issue.htmlUrl}")
                    is IssueService.IssueResult.Error ->
                        Log.w(TAG, "Failed to auto-report crash: ${result.message}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to auto-report crash", e)
            } finally {
                // Cleared even on failure: a crash that can't be reported (auth expired
                // mid-flight, network down, repo access revoked) shouldn't be retried forever
                // on every subsequent launch — one attempt per crash, same as the prompt path
                // below only ever asks once.
                storage.clear()
            }
        }
    }

    private fun promptToReport(context: Context, report: PendingCrashReport, storage: PendingCrashStorage) {
        // The prompt itself (not built here — see CrashPromptActivity) decides whether the
        // user wants to report. Either way this pending report is consumed now: accepting
        // routes the formatted title/body into GHReporterActivity's own sign-in-then-review
        // flow via its prefill extras, which is a fresh, independent submission from that
        // point on, not a continuation of this stored file.
        storage.clear()

        val intent =
            Intent(context, CrashPromptActivity::class.java).apply {
                putExtra(GHReporterActivity.EXTRA_PREFILL_TITLE, CrashReportFormatter.title(report))
                putExtra(GHReporterActivity.EXTRA_PREFILL_BODY, CrashReportFormatter.body(report))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to show crash report prompt", e)
        }
    }

    private const val TAG = "GHReporter.Crash"
}
