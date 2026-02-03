package com.ghreporter.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ghreporter.GHReporter

class ReportNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (GHReporterNotificationManager.isReportAction(intent)) {
            GHReporter.startReporting(context)
        }
    }
}
