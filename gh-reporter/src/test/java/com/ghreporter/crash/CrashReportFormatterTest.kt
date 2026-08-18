package com.ghreporter.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportFormatterTest {

    private fun sampleReport(
        stackTrace: String =
            "java.lang.IllegalStateException: something broke\n\tat com.example.Foo.bar(Foo.kt:42)",
        timberLogs: String? = "12:00:00 D/App: hello",
    ) =
        PendingCrashReport(
            threadName = "main",
            exceptionType = "java.lang.IllegalStateException",
            exceptionMessage = "something broke",
            stackTrace = stackTrace,
            timberLogs = timberLogs,
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
    fun `issueTitle includes exception type and origin line`() {
        val title = sampleReport().issueTitle()

        assertTrue(title.startsWith("Crash: java.lang.IllegalStateException at"))
        assertTrue(title.contains("com.example.Foo.bar"))
    }

    @Test
    fun `issueTitle falls back to just the exception type when stack trace has no second line`() {
        val title = sampleReport(stackTrace = "java.lang.IllegalStateException: something broke").issueTitle()

        assertEquals("Crash: java.lang.IllegalStateException", title)
    }

    @Test
    fun `issueTitle is capped to 200 characters`() {
        val hugeOrigin = "at com.example." + "a".repeat(500) + ".bar(Foo.kt:1)"
        val title =
            sampleReport(stackTrace = "java.lang.IllegalStateException: x\n\t$hugeOrigin").issueTitle()

        assertEquals(200, title.length)
    }

    @Test
    fun `body includes exception type, message, and stack trace`() {
        val body = CrashReportFormatter.body(sampleReport())

        assertTrue(body.contains("java.lang.IllegalStateException: something broke"))
        assertTrue(body.contains("at com.example.Foo.bar(Foo.kt:42)"))
    }

    @Test
    fun `body includes device and app info`() {
        val body = CrashReportFormatter.body(sampleReport())

        assertTrue(body.contains("Google Pixel 7a"))
        assertTrue(body.contains("15 (API 35)"))
        assertTrue(body.contains("com.example.app"))
        assertTrue(body.contains("1.2.3 (45)"))
    }

    @Test
    fun `body includes timber logs when present`() {
        val body = CrashReportFormatter.body(sampleReport(timberLogs = "12:00:00 D/App: hello"))

        assertTrue(body.contains("Recent logs"))
        assertTrue(body.contains("12:00:00 D/App: hello"))
    }

    @Test
    fun `body omits the logs section when there are no timber logs`() {
        val body = CrashReportFormatter.body(sampleReport(timberLogs = null))

        assertFalse(body.contains("Recent logs"))
    }

    @Test
    fun `body omits the logs section when timber logs are blank`() {
        val body = CrashReportFormatter.body(sampleReport(timberLogs = "   "))

        assertFalse(body.contains("Recent logs"))
    }
}
