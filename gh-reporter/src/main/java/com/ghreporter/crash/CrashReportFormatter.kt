package com.ghreporter.crash

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Turns a [PendingCrashReport] into the title/body pair used for both the silent-submit and the review-UI paths, so the two never drift apart. */
internal object CrashReportFormatter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)

    fun title(report: PendingCrashReport): String = report.issueTitle()

    fun body(report: PendingCrashReport): String = buildString {
        appendLine("## Crash Report")
        appendLine()
        appendLine("This issue was filed automatically after the app crashed and closed.")
        appendLine()
        appendLine(
            "**Crashed at:** ${dateFormat.format(Date(report.crashedAtEpochMillis))} " +
                "(thread: `${report.threadName}`)",
        )
        appendLine()
        appendLine("## Exception")
        appendLine()
        appendLine("```")
        appendLine("${report.exceptionType}: ${report.exceptionMessage.orEmpty()}")
        appendLine("```")
        appendLine()
        appendLine("<details><summary>Full stack trace</summary>")
        appendLine()
        appendLine("```")
        append(report.stackTrace.trimEnd())
        appendLine()
        appendLine("```")
        appendLine()
        appendLine("</details>")
        appendLine()
        appendLine("## Device Information")
        appendLine()
        appendLine("| Property | Value |")
        appendLine("|----------|-------|")
        appendLine("| Device | ${report.deviceManufacturer} ${report.deviceModel} |")
        appendLine("| Android Version | ${report.androidRelease} (API ${report.androidSdkInt}) |")
        appendLine()
        appendLine("## App Information")
        appendLine()
        appendLine("| Property | Value |")
        appendLine("|----------|-------|")
        appendLine("| Package | ${report.appPackage} |")
        appendLine("| Version | ${report.appVersionName ?: "unknown"} (${report.appVersionCode}) |")
        appendLine()
        if (!report.timberLogs.isNullOrBlank()) {
            appendLine("<details><summary>Recent logs</summary>")
            appendLine()
            appendLine("```")
            append(report.timberLogs.trimEnd())
            appendLine()
            appendLine("```")
            appendLine()
            appendLine("</details>")
            appendLine()
        }
        appendLine("---")
        appendLine("*Reported automatically via GHReporter SDK crash capture*")
    }
}
