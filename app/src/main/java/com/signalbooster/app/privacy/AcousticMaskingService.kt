package com.signalbooster.app.privacy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.signalbooster.app.MainActivity
import com.signalbooster.app.domain.interfaces.AcousticMaskingController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service for user-visible Acoustic Masking status per PRD FR-09.
 * Strictly mediaPlayback type; NO microphone capture.
 */
@AndroidEntryPoint
class AcousticMaskingService : Service() {

    @Inject
    lateinit var acousticController: AcousticMaskingController

    private val notificationId = 1001
    private val channelId = "signalbooster_acoustic_channel"
    private val serviceScope = CoroutineScope(Dispatchers.Default)

    companion object {
        const val ACTION_STOP_MASKING = "com.signalbooster.app.ACTION_STOP_MASKING"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_MASKING) {
            serviceScope.launch {
                acousticController.stopMasking()
            }
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        startForeground(notificationId, notification)
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Acoustic Masking Privacy",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays active status for local privacy acoustic masking."
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, AcousticMaskingService::class.java).apply {
            action = ACTION_STOP_MASKING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Acoustic Masking Active")
            .setContentText("Local synthetic noise is running to protect nearby speech.")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}
