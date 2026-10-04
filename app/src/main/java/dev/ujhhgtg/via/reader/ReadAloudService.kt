package dev.ujhhgtg.via.reader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.IBinder
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.common.ViaIntents

/** Original service/receiver behavior: pause detaches its notification; stop/end removes it. */
class ReadAloudService : Service() {
    private lateinit var controller: ReadAloudController
    private lateinit var notifications: NotificationManager
    private var audioDevices: AudioDeviceCallback? = null
    private val listener: (ReadAloudTask?) -> Unit = { task ->
        when {
            task == null || task.isFinished -> finish(true)
            else -> {
                notifications.notify(NOTIFICATION, notification(task))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        controller = ReadAloudController.get(this)
        notifications = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notifications.deleteNotificationChannel(LEGACY_CHANNEL)
        notifications.createNotificationChannel(NotificationChannel(
            CHANNEL, getString(R.string.read_aloud), NotificationManager.IMPORTANCE_LOW,
        ))
        startForeground(NOTIFICATION, notification(controller.task))
        controller.addListener(listener)
        audioDevices = object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(devices: Array<out AudioDeviceInfo>?) { send(this@ReadAloudService, PAUSE) }
        }.also { (getSystemService(AUDIO_SERVICE) as AudioManager).registerAudioDeviceCallback(it, null) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            PLAY -> controller.playNow(intent.getStringExtra("id"), if (intent.hasExtra("index")) intent.getIntExtra("index", 0) else null)
            PAUSE -> if (controller.pauseNow(intent.getStringExtra("id"))) finish(false)
            STOP -> controller.stopNow()
        }
        if (controller.task == null) finish(true)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        controller.removeListener(listener)
        audioDevices?.let { (getSystemService(AUDIO_SERVICE) as AudioManager).unregisterAudioDeviceCallback(it) }
        super.onDestroy()
    }

    private fun finish(remove: Boolean) {
        stopForeground(if (remove) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun notification(task: ReadAloudTask?): Notification {
        val playing = task?.isPlaying == true
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.setAction(OPEN_PANEL)
        val toggle = Intent(this, ReadAloudReceiver::class.java).setAction(if (playing) PAUSE else PLAY).putExtra("id", task?.id)
        val action = PendingIntent.getBroadcast(this, 0, toggle, flags)
        val builder = Notification.Builder(this, CHANNEL)
        builder.setSmallIcon(R.drawable.reader_notification)
            .setContentTitle(task?.title ?: getString(R.string.untitled))
            .setContentText(task?.url?.let(::displayHost).orEmpty())
            .setOngoing(playing)
            .addAction(if (playing) R.drawable.reader_notification_pause else R.drawable.reader_notification_play,
                getString(if (playing) R.string.download_action_pause else R.string.download_action_resume), action)
        builder.setCategory(if (playing) Notification.CATEGORY_PROGRESS else Notification.CATEGORY_STATUS)
        launch?.let { builder.setContentIntent(PendingIntent.getActivity(this, 0, it, flags)) }
        return builder.build()
    }

    /** j8.s -> i6.g0.b strips the port and an m./www. prefix only with a further dot. */
    private fun displayHost(url: String): String {
        val host = dev.ujhhgtg.via.browser.DocumentPolicy.host(url)
        return when {
            host.startsWith("m.") && host.indexOf('.', 2) >= 0 -> host.substring(2)
            host.startsWith("www.") && host.indexOf('.', 4) >= 0 -> host.substring(4)
            else -> host
        }
    }

    companion object {
        const val PLAY = "dev.ujhhgtg.via.reader.PLAY"
        const val PAUSE = "dev.ujhhgtg.via.reader.PAUSE"
        const val STOP = "dev.ujhhgtg.via.reader.STOP"
        const val OPEN_PANEL = ViaIntents.ACTION_READ_ALOUD
        private const val LEGACY_CHANNEL = "dev.ujhhgtg.via.CHANNEL.READ_ALOUD"
        private const val CHANNEL = "dev.ujhhgtg.via.CHANNEL.READ_ALOUD2"
        private const val NOTIFICATION = 2
        fun send(context: Context, action: String, id: String? = null, index: Int? = null) {
            val intent = Intent(context, ReadAloudService::class.java).setAction(action).putExtra("id", id)
            if (index != null) intent.putExtra("index", index)
            context.startForegroundService(intent)
        }
    }
}

class ReadAloudReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == ReadAloudService.PLAY || action == ReadAloudService.PAUSE || action == ReadAloudService.STOP) {
            ReadAloudService.send(context, action, intent.getStringExtra("id"))
        }
    }
}
