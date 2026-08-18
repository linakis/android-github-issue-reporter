package com.ghreporter.crash

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PendingCrashStorageTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private fun storage(): PendingCrashStorage {
        val context = mockk<Context>()
        every { context.filesDir } returns tempFolder.root
        return PendingCrashStorage(context)
    }

    private fun sampleReport() =
        PendingCrashReport(
            threadName = "main",
            exceptionType = "java.lang.IllegalStateException",
            exceptionMessage = "something broke",
            stackTrace = "java.lang.IllegalStateException: something broke\n\tat com.example.Foo.bar(Foo.kt:42)",
            timberLogs = "12:00:00 D/App: hello",
            deviceManufacturer = "Google",
            deviceModel = "Pixel 7a",
            androidRelease = "15",
            androidSdkInt = 35,
            appPackage = "com.example.app",
            appVersionName = "1.2.3",
            appVersionCode = 45,
            crashedAtEpochMillis = 1_700_000_000_000L,
        )

    @Test
    fun `load returns null when nothing was saved`() {
        assertNull(storage().load())
    }

    @Test
    fun `save then load round-trips the report`() {
        val subject = storage()
        val report = sampleReport()

        subject.save(report)
        val loaded = subject.load()

        assertEquals(report, loaded)
    }

    @Test
    fun `clear removes the pending report`() {
        val subject = storage()
        subject.save(sampleReport())

        subject.clear()

        assertNull(subject.load())
    }

    @Test
    fun `save overwrites a previously pending report`() {
        val subject = storage()
        subject.save(sampleReport())
        val second = sampleReport().copy(exceptionMessage = "a different crash")

        subject.save(second)

        assertEquals(second, subject.load())
    }
}
