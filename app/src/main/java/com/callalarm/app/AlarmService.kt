package com.callalarm.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Plays the phone's own ringtone (looping) + vibration while the incoming-call
 * screen is showing. Keeps ringing until the user answers (or 10 minutes pass).
 */
class AlarmService : Service() {

    companion object {
        const val ACTION_START = "com.callalarm.app.START_RING"
        const val ACTION_STOP = "com.callalarm.app.STOP_RING"
        const val ACTION_STOPPED = "com.callalarm.app.RING_STOPPED"
        const val EXTRA_ID = "alarm_id"
        const val EXTRA_LABEL = "label"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_AUTO_ANSWER = "auto_answer"

        private const val CHANNEL_ID = "incoming_call_alarm"
        private const val NOTIF_ID = 4242
        private const val MAX_RING_MS = 10 * 60 * 1000L
        private const val PI_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        private const val FALLBACK_CHANNEL_ID = "incoming_call_fallback"

        /**
         * Backup path used when the foreground service can't be started from the
         * background: a full-screen call notification whose own channel plays the
         * ringtone (repeating) and vibrates.
         */
        fun showFallbackCall(ctx: Context, id: Int, label: String, message: String) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(FALLBACK_CHANNEL_ID) == null) {
                val uri = RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                val ch = NotificationChannel(
                    FALLBACK_CHANNEL_ID, "Incoming alarm calls (backup)", NotificationManager.IMPORTANCE_HIGH
                )
                ch.setSound(
                    uri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                ch.enableVibration(true)
                ch.vibrationPattern = longArrayOf(0, 1000, 800, 1000, 800)
                ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                nm.createNotificationChannel(ch)
            }
            val full = PendingIntent.getActivity(
                ctx, 1, IncomingCallActivity.intent(ctx, id, label, message, false), PI_FLAGS
            )
            val answer = PendingIntent.getActivity(
                ctx, 2, IncomingCallActivity.intent(ctx, id, label, message, true), PI_FLAGS
            )
            val n = Notification.Builder(ctx, FALLBACK_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_call)
                .setContentTitle(label)
                .setContentText("Incoming call")
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setFullScreenIntent(full, true)
                .setContentIntent(full)
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(ctx, R.drawable.ic_call), "Answer", answer
                    ).build()
                )
                .build()
            n.flags = n.flags or Notification.FLAG_INSISTENT // keep repeating the ringtone
            nm.notify(NOTIF_ID, n)
            try {
                ctx.startActivity(IncomingCallActivity.intent(ctx, id, label, message, false))
            } catch (_: Exception) {
            }
        }

        fun startIntent(ctx: Context, id: Int, label: String, message: String): Intent =
            Intent(ctx, AlarmService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_MESSAGE, message)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable { onTimeout() }
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var label = "Alarm"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_STOP) {
            stopRinging()
            stopForeground(STOP_FOREGROUND_REMOVE)
            try {
                getSystemService(NotificationManager::class.java).cancel(NOTIF_ID) // also clears the backup notification
            } catch (_: Exception) {
            }
            stopSelf()
            sendBroadcast(Intent(ACTION_STOPPED).setPackage(packageName))
            return START_NOT_STICKY
        }
        startRinging(intent)
        return START_NOT_STICKY
    }

    private fun startRinging(intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        label = intent.getStringExtra(EXTRA_LABEL) ?: "Alarm"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""

        createChannel()

        val full = PendingIntent.getActivity(
            this, 1, IncomingCallActivity.intent(this, id, label, message, false), PI_FLAGS
        )
        val answer = PendingIntent.getActivity(
            this, 2, IncomingCallActivity.intent(this, id, label, message, true), PI_FLAGS
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(label)
            .setContentText("Incoming call")
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_call), "Answer", answer
                ).build()
            )
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        stopRinging() // in case another alarm was already ringing
        acquireWakeLock()
        ensureAudible()
        startSound()
        startVibration()

        // If the app is allowed to, pop the call screen right away
        // (the full-screen notification covers the locked-screen case).
        try {
            startActivity(IncomingCallActivity.intent(this, id, label, message, false))
        } catch (_: Exception) {
        }

        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, MAX_RING_MS)
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(CHANNEL_ID, "Incoming alarm calls", NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(null, null) // the service plays the ringtone itself
            ch.enableVibration(false)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "callalarm:ring").apply {
                acquire(MAX_RING_MS + 5_000L)
            }
        } catch (_: Exception) {
        }
    }

    /** If the alarm volume is at 0 the alarm would be silent - lift it to 70%. */
    private fun ensureAudible() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (am.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
                val target = (am.getStreamMaxVolume(AudioManager.STREAM_ALARM) * 0.7).toInt()
                am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
            }
        } catch (_: Exception) {
        }
    }

    private fun alarmAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun tryPlay(uri: Uri?): MediaPlayer? {
        if (uri == null) return null
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(alarmAttributes())
            mp.setDataSource(this, uri)
            mp.isLooping = true
            mp.prepare()
            mp.start()
            mp
        } catch (_: Exception) {
            try {
                mp.release()
            } catch (_: Exception) {
            }
            null
        }
    }

    private fun startSound() {
        val candidates = listOf(
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        )
        for (uri in candidates) {
            val mp = tryPlay(uri)
            if (mp != null) {
                player = mp
                return
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        try {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            vibrator = v
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 1000, 800), 0)
            v.vibrate(effect, alarmAttributes())
        } catch (_: Exception) {
        }
    }

    private fun stopRinging() {
        handler.removeCallbacks(timeoutRunnable)
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
        vibrator = null
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun onTimeout() {
        stopRinging()
        stopForeground(STOP_FOREGROUND_REMOVE)
        showMissedCall()
        stopSelf()
        sendBroadcast(Intent(ACTION_STOPPED).setPackage(packageName))
    }

    private fun showMissedCall() {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            val open = PendingIntent.getActivity(this, 3, Intent(this, MainActivity::class.java), PI_FLAGS)
            val n = Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_call)
                .setContentTitle("Missed alarm call")
                .setContentText("$label called but you never picked up")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIF_ID + 1, n)
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }
}
