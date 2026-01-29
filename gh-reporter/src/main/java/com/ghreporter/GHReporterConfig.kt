package com.ghreporter

/**
 * Configuration for GHReporter SDK.
 *
 * @property githubOwner The GitHub repository owner (username or organization)
 * @property githubRepo The GitHub repository name
 * @property githubClientId GitHub OAuth App client ID (for Device Flow authentication)
 * @property maxTimberLogEntries Maximum number of Timber log entries to keep in memory (default: 500)
 * @property maxOkHttpLogEntries Maximum number of OkHttp request/response pairs to keep (default: 50)
 * @property maxLogcatLines Maximum number of logcat lines to capture (default: 500)
 * @property enableLogcat Whether to enable logcat collection (default: true)
 * @property defaultLabels Default labels to apply to created issues
 * @property shakeThresholdG Shake detection threshold in G-force units (default: 2.7)
 * @property shakeCooldownMs Cooldown between shake detections in milliseconds (default: 1000)
 * @property includeDeviceInfo Whether to include device info in issue body (default: true)
 * @property includeAppInfo Whether to include app version info in issue body (default: true)
 */
data class GHReporterConfig(
    val githubOwner: String,
    val githubRepo: String,
    val githubClientId: String,
    val maxTimberLogEntries: Int = 500,
    val maxOkHttpLogEntries: Int = 50,
    val maxLogcatLines: Int = 500,
    val enableLogcat: Boolean = true,
    val defaultLabels: List<String> = emptyList(),
    val shakeThresholdG: Float = 2.7f,
    val shakeCooldownMs: Long = 1000L,
    val includeDeviceInfo: Boolean = true,
    val includeAppInfo: Boolean = true
) {
    init {
        require(githubOwner.isNotBlank()) { "githubOwner must not be blank" }
        require(githubRepo.isNotBlank()) { "githubRepo must not be blank" }
        require(githubClientId.isNotBlank()) { "githubClientId must not be blank" }
        require(maxTimberLogEntries > 0) { "maxTimberLogEntries must be positive" }
        require(maxOkHttpLogEntries > 0) { "maxOkHttpLogEntries must be positive" }
        require(maxLogcatLines > 0) { "maxLogcatLines must be positive" }
        require(shakeThresholdG > 0) { "shakeThresholdG must be positive" }
        require(shakeCooldownMs > 0) { "shakeCooldownMs must be positive" }
    }

    /**
     * Builder pattern for Java interoperability.
     */
    class Builder(
        private val githubOwner: String,
        private val githubRepo: String,
        private val githubClientId: String
    ) {
        private var maxTimberLogEntries: Int = 500
        private var maxOkHttpLogEntries: Int = 50
        private var maxLogcatLines: Int = 500
        private var enableLogcat: Boolean = true
        private var defaultLabels: List<String> = emptyList()
        private var shakeThresholdG: Float = 2.7f
        private var shakeCooldownMs: Long = 1000L
        private var includeDeviceInfo: Boolean = true
        private var includeAppInfo: Boolean = true

        fun maxTimberLogEntries(value: Int) = apply { maxTimberLogEntries = value }
        fun maxOkHttpLogEntries(value: Int) = apply { maxOkHttpLogEntries = value }
        fun maxLogcatLines(value: Int) = apply { maxLogcatLines = value }
        fun enableLogcat(value: Boolean) = apply { enableLogcat = value }
        fun defaultLabels(value: List<String>) = apply { defaultLabels = value }
        fun shakeThresholdG(value: Float) = apply { shakeThresholdG = value }
        fun shakeCooldownMs(value: Long) = apply { shakeCooldownMs = value }
        fun includeDeviceInfo(value: Boolean) = apply { includeDeviceInfo = value }
        fun includeAppInfo(value: Boolean) = apply { includeAppInfo = value }

        fun build() = GHReporterConfig(
            githubOwner = githubOwner,
            githubRepo = githubRepo,
            githubClientId = githubClientId,
            maxTimberLogEntries = maxTimberLogEntries,
            maxOkHttpLogEntries = maxOkHttpLogEntries,
            maxLogcatLines = maxLogcatLines,
            enableLogcat = enableLogcat,
            defaultLabels = defaultLabels,
            shakeThresholdG = shakeThresholdG,
            shakeCooldownMs = shakeCooldownMs,
            includeDeviceInfo = includeDeviceInfo,
            includeAppInfo = includeAppInfo
        )
    }

    companion object {
        /**
         * Creates a builder for GHReporterConfig.
         */
        @JvmStatic
        fun builder(
            githubOwner: String,
            githubRepo: String,
            githubClientId: String
        ) = Builder(githubOwner, githubRepo, githubClientId)
    }
}
