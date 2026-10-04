package dev.ujhhgtg.via.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.common.ViaIntents
import dev.ujhhgtg.via.Shell

/** mark.via.service.DownloadService and mark.via.download.d: process queue notifications. */
class DownloadService : Service() {
    private lateinit var coordinator: DownloadCoordinator
    private val listener: (DownloadRecord) -> Unit = { record ->
        // b1.c4 owns the delayed cancellation after deletion, independently of
        // this Service. A removed row must not cancel its notification early.
        if (coordinator.get(record.id) != null) updateTaskNotification(record)
        if ((record.isComplete || record.isFailed || record.state == DownloadState.PAUSED || record.state == DownloadState.WAITING_NETWORK) && !coordinator.hasRunning()) stopWhenIdle()
    }

    override fun onCreate() {
        super.onCreate()
        coordinator = DownloadCoordinator.get(this)
        coordinator.addListener(listener)
        createChannels()
        startForeground(FOREGROUND_ID, foregroundNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(EXTRA_ID, 0) ?: 0
        when (intent?.action) {
            ACTION_RESUME -> if (id > 0) coordinator.start(id)
            ACTION_PAUSE -> if (id > 0 && coordinator.pause(id) && !coordinator.hasRunning()) stopWhenIdle()
            ACTION_REDOWNLOAD -> if (id > 0) coordinator.redownload(id)
            // These are the original literal mappings, verified in Service and c5.b/m5.i smali.
            ACTION_PAUSE_ALL -> if (!coordinator.startPending()) stopWhenIdle()
            ACTION_RESUME_ALL -> if (coordinator.pauseAll()) stopWhenIdle()
        }
        return START_STICKY
    }

    @Suppress("DEPRECATION")
    private fun stopWhenIdle() { stopForeground(true); stopSelf() }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { coordinator.removeListener(listener); super.onDestroy() }
    private fun manager() = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    private fun pendingFlags() = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    private fun browserPending() = PendingIntent.getActivity(this, 0,
        Intent(this, Shell::class.java).setAction(ViaIntents.ACTION_DOWNLOADER).setPackage(packageName), pendingFlags())
    private fun builder(channel: String) = Notification.Builder(this, channel)

    private fun createChannels() {
        manager().createNotificationChannelGroup(NotificationChannelGroup(GENERAL_GROUP, getString(R.string.settings_general)))
        manager().deleteNotificationChannel(LEGACY_FOREGROUND_CHANNEL)
        manager().createNotificationChannel(NotificationChannel(FOREGROUND_CHANNEL,
            getString(R.string.notification_channel_foreground), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        manager().createNotificationChannel(NotificationChannel(TASK_CHANNEL,
            getString(R.string.notification_channel_downloads), NotificationManager.IMPORTANCE_LOW).apply { group = GENERAL_GROUP })
    }

    private fun foregroundNotification(): Notification = builder(FOREGROUND_CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentTitle(getString(R.string.app_downloading_files))
        .setContentText(getString(R.string.app_downloading_files_subtitle))
        .setContentIntent(browserPending()).setCategory(Notification.CATEGORY_PROGRESS)
        // n.h.d.m(true) -> NotificationCompat.setSilent: n.s0 uses the silent group and suppresses child alerts.
        .setSound(null).setVibrate(null).setDefaults(0).setGroup("silent")
        .setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY).build()

    private fun updateTaskNotification(record: DownloadRecord) {
        val category = when { record.isComplete -> 1; record.isFailed -> 2; record.state == DownloadState.WAITING_NETWORK -> 3; record.isActive -> 4; record.state == DownloadState.PAUSED -> 5; else -> 0 }
        val now = SystemClock.elapsedRealtime()
        val previous = notifications[record.id]
        if (previous != null && previous.category == category && now - previous.lastUpdate < 1000) return
        val state = previous ?: NotificationState()
        state.category = category; state.lastUpdate = now; notifications[record.id] = state
        val running = record.isActive && record.state != DownloadState.WAITING_NETWORK
        val notification = builder(TASK_CHANNEL).setWhen(state.createdAt).setContentTitle(record.name)
            .setContentIntent(if (record.isComplete) completedPending(record) else browserPending())
            .setOngoing(running).setAutoCancel(!running)
        if (!record.isComplete && !record.isFailed) {
            val detail = DownloadPresentation.detail(this, record, coordinator.speed(record.id).takeIf { it > 0 } ?: -1)
            notification.setContentText(detail).setStyle(Notification.BigTextStyle().bigText(detail))
                .setSmallIcon(if (running) android.R.drawable.stat_sys_download else R.drawable.download_notification_pause)
                .setCategory(if (running) Notification.CATEGORY_PROGRESS else Notification.CATEGORY_STATUS)
                // download.d.h: long-to-float, div-float, mul-float, float-to-int.
                .setProgress(100, if (record.totalSize > 0) (record.downloadedSize.toFloat() / record.totalSize.toFloat() * 100f).toInt() else 50, record.totalSize <= 0)
            // Waiting-network (95) is active in g5.b.d, hence its action is still Pause.
            val action = if (record.isActive) ACTION_PAUSE else ACTION_RESUME
            val pending = PendingIntent.getBroadcast(this, 0, Intent(this, DownloadReceiver::class.java)
                .setAction(action).putExtra(EXTRA_ID, record.id), pendingFlags())
            @Suppress("DEPRECATION")
            notification.addAction(if (record.isActive) R.drawable.download_notification_pause else R.drawable.download_notification_resume,
                getString(if (record.isActive) R.string.download_action_pause else R.string.download_action_resume), pending)
        } else {
            notification.setContentText(if (record.isComplete) getString(R.string.download_completed) else DownloadPresentation.detail(this, record))
                .setSmallIcon(if (record.isComplete) android.R.drawable.stat_sys_download_done else R.drawable.download_notification_error)
                .setCategory(if (record.isComplete) Notification.CATEGORY_STATUS else Notification.CATEGORY_ERROR)
            manager().cancel(notificationId(record.id))
        }
        runCatching { manager().notify(notificationId(record.id), notification.build()) }
    }

    private fun completedPending(record: DownloadRecord): PendingIntent =
        DownloadFiles.openIntent(this, record)?.let { PendingIntent.getActivity(this, 0, it, pendingFlags()) } ?: browserPending()

    companion object {
        const val ACTION_PAUSE = "dev.ujhhgtg.via.receiver.DownloadReceiver.PAUSE"
        const val ACTION_RESUME = "dev.ujhhgtg.via.receiver.DownloadReceiver.RESUME"
        const val ACTION_REDOWNLOAD = "dev.ujhhgtg.via.receiver.DownloadReceiver.REDOWNLOAD"
        const val ACTION_PAUSE_ALL = "dev.ujhhgtg.via.receiver.DownloadReceiver.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "dev.ujhhgtg.via.receiver.DownloadReceiver.RESUME_ALL"
        const val EXTRA_ID = "id"
        private const val LEGACY_FOREGROUND_CHANNEL = "dev.ujhhgtg.via.DOWNLOAD_FORGROUND"
        private const val FOREGROUND_CHANNEL = "dev.ujhhgtg.via.FORGROUND"
        private const val TASK_CHANNEL = "dev.ujhhgtg.via.DOWNLOAD"
        private const val GENERAL_GROUP = "dev.ujhhgtg.via.GENERAL_GROUP"
        private const val FOREGROUND_ID = 1
        private data class NotificationState(val createdAt: Long = System.currentTimeMillis(), var lastUpdate: Long = 0, var category: Int = 0)
        private val notifications = HashMap<Long, NotificationState>()
        private fun notificationId(id: Long) = (id xor (id ushr 32)).toInt()
        /** download.d.b: process-owned task notifications outlive the foreground Service. */
        internal fun cancelTaskNotification(context: Context, id: Long) {
            if (notifications.remove(id) == null) return
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notificationId(id))
        }
        fun intent(context: Context, action: String, id: Long = 0) =
            Intent(context, DownloadService::class.java).setAction(action).putExtra(EXTRA_ID, id)
        fun start(context: Context, action: String = ACTION_PAUSE_ALL, id: Long = 0) { context.startForegroundService(intent(context, action, id)) }
    }
}
