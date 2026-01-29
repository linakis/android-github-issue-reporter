package com.ghreporter.sample

import android.app.Application
import com.ghreporter.GHReporter
import com.ghreporter.GHReporterConfig
import okhttp3.OkHttpClient
import timber.log.Timber

/**
 * Sample application demonstrating GHReporter SDK integration.
 */
class SampleApp : Application() {

    lateinit var okHttpClient: OkHttpClient
        private set

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize GHReporter
        GHReporter.init(
            context = this,
            config = GHReporterConfig(
                githubOwner = "your-org",           // TODO: Replace with your GitHub org/user
                githubRepo = "your-repo",           // TODO: Replace with your repo name
                githubClientId = "Iv1.xxxxxxxxxx",  // TODO: Replace with your OAuth App client ID
                defaultLabels = listOf("bug", "from-app"),
                maxTimberLogEntries = 500,
                maxOkHttpLogEntries = 50,
                maxLogcatLines = 500,
                shakeThresholdG = 2.7f,
                shakeCooldownMs = 1000L,
                includeDeviceInfo = true,
                includeAppInfo = true
            )
        )

        // 2. Plant Timber tree for log collection
        Timber.plant(Timber.DebugTree())
        Timber.plant(GHReporter.getTimberTree())

        // 3. Create OkHttpClient with GHReporter interceptor
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(GHReporter.getOkHttpInterceptor())
            .build()

        Timber.i("SampleApp initialized with GHReporter SDK")
    }
}
