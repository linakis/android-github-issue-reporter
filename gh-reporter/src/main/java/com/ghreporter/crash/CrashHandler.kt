package com.ghreporter.crash

import android.content.Context
import android.os.Build
import android.util.Log
import com.ghreporter.collectors.GHReporterTree
import java.io.PrintWriter
import java.io.StringWriter

/**
 * [Thread.UncaughtExceptionHandler] that snapshots a crash to disk and then delegates to
 * whatever handler was previously installed (the platform's default handler, another crash
 * reporter already in the chain, etc.) so the app still crashes exactly as it would without
 * GHReporter — no hang, no swallowed crash, no interference with other tooling. The saved
 * report is picked up and submitted as a GitHub issue on the *next* successful launch (see
 * [com.ghreporter.GHReporter.checkForPendingCrash]), since interactive GitHub sign-in isn't
 * possible from inside a dying process.
 */
internal class CrashHandler(
    private val context: Context,
    private val appPackage: String,
    private val appVersionName: String?,
    private val appVersionCode: Long,
    private val timberTree: GHReporterTree?,
    private val previousHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val report = buildReport(thread, throwable)
            PendingCrashStorage(context).save(report)
        } catch (e: Exception) {
            // Never let crash *capture* introduce a second crash or mask the first one.
            Log.e(TAG, "Failed to persist crash report", e)
        }

        previousHandler?.uncaughtException(thread, throwable)
    }

    private fun buildReport(thread: Thread, throwable: Throwable): PendingCrashReport {
        val stackTraceWriter = StringWriter()
        throwable.printStackTrace(PrintWriter(stackTraceWriter))

        return PendingCrashReport(
            threadName = thread.name,
            exceptionType = throwable.javaClass.name,
            exceptionMessage = throwable.message,
            stackTrace = stackTraceWriter.toString(),
            timberLogs = timberTree?.getLogsAsString(),
            deviceManufacturer = Build.MANUFACTURER,
            deviceModel = Build.MODEL,
            androidRelease = Build.VERSION.RELEASE,
            androidSdkInt = Build.VERSION.SDK_INT,
            appPackage = appPackage,
            appVersionName = appVersionName,
            appVersionCode = appVersionCode,
            crashedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    private companion object {
        const val TAG = "GHReporter.CrashHandler"
    }
}
