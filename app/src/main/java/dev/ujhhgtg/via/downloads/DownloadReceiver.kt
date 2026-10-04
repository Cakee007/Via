package dev.ujhhgtg.via.downloads

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** mark.via.receiver.DownloadReceiver: notification commands are forwarded unchanged to the service. */
class DownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in setOf(DownloadService.ACTION_PAUSE, DownloadService.ACTION_RESUME,
                DownloadService.ACTION_REDOWNLOAD, DownloadService.ACTION_PAUSE_ALL, DownloadService.ACTION_RESUME_ALL)) return
        runCatching { context.startService(DownloadService.intent(context.applicationContext, action, intent.getLongExtra(DownloadService.EXTRA_ID, 0))) }
    }
}
