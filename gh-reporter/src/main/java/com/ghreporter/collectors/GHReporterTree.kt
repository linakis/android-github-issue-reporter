package com.ghreporter.collectors

import android.util.Log
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * A Timber Tree that collects logs for GHReporter.
 *
 * Uses a ring buffer to keep the most recent log entries up to [maxEntries].
 * Thread-safe for concurrent log collection.
 *
 * Usage:
 * ```kotlin
 * Timber.plant(GHReporter.getTimberTree())
 * ```
 */
class GHReporterTree(
    private val maxEntries: Int = 500
) : Timber.Tree() {

    private val logs = ConcurrentLinkedDeque<LogEntry>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /**
     * Represents a single log entry.
     */
    data class LogEntry(
        val timestamp: Long,
        val formattedTime: String,
        val priority: Int,
        val priorityLabel: String,
        val tag: String?,
        val message: String,
        val throwable: Throwable?
    ) {
        /**
         * Format the log entry as a string for display.
         */
        fun format(): String {
            val throwableStr = throwable?.let { "\n${Log.getStackTraceString(it)}" } ?: ""
            return "$formattedTime $priorityLabel/${tag ?: "GHReporter"}: $message$throwableStr"
        }
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val now = System.currentTimeMillis()
        val entry = LogEntry(
            timestamp = now,
            formattedTime = dateFormat.format(Date(now)),
            priority = priority,
            priorityLabel = getPriorityLabel(priority),
            tag = tag,
            message = message,
            throwable = t
        )

        logs.addLast(entry)

        // Trim to max size
        while (logs.size > maxEntries) {
            logs.pollFirst()
        }
    }

    /**
     * Get all collected log entries.
     *
     * @return List of log entries, oldest first
     */
    fun getLogs(): List<LogEntry> = logs.toList()

    /**
     * Get logs formatted as a single string.
     *
     * @return Formatted log string with each entry on a new line
     */
    fun getLogsAsString(): String {
        return logs.joinToString("\n") { it.format() }
    }

    /**
     * Get the number of collected logs.
     */
    fun size(): Int = logs.size

    /**
     * Clear all collected logs.
     */
    fun clear() {
        logs.clear()
    }

    /**
     * Get logs filtered by minimum priority.
     *
     * @param minPriority Minimum log priority (e.g., Log.WARN)
     * @return Filtered list of log entries
     */
    fun getLogsWithMinPriority(minPriority: Int): List<LogEntry> {
        return logs.filter { it.priority >= minPriority }
    }

    /**
     * Get logs from the last N milliseconds.
     *
     * @param durationMs Duration in milliseconds
     * @return Logs within the specified time window
     */
    fun getLogsFromLast(durationMs: Long): List<LogEntry> {
        val cutoff = System.currentTimeMillis() - durationMs
        return logs.filter { it.timestamp >= cutoff }
    }

    private fun getPriorityLabel(priority: Int): String = when (priority) {
        Log.VERBOSE -> "V"
        Log.DEBUG -> "D"
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        Log.ASSERT -> "A"
        else -> "?"
    }

    companion object {
        /**
         * Priority constants for convenience.
         */
        const val VERBOSE = Log.VERBOSE
        const val DEBUG = Log.DEBUG
        const val INFO = Log.INFO
        const val WARN = Log.WARN
        const val ERROR = Log.ERROR
        const val ASSERT = Log.ASSERT
    }
}
