package com.example.obdy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Keeps the app alive while it is connected to the car. Without it Android froze the app when the screen locked or another
 * app came to the front (a 20 minute drive lost 15 minutes, one command logged 638 s). Shows one quiet notification and
 * holds a partial wake lock only while connected. Writes nothing to any drive; it only keeps the process running.
 */
class KeepAlive : Service() {
    private var lock: PowerManager.WakeLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { release(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CH, "Connected to the car", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n: Notification = NotificationCompat.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_stat_obdy)
            .setContentTitle("Obdy is connected to the car")
            .setContentText("Recording and logging continue with the screen off")
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_SERVICE).build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) else startForeground(ID, n)
        } catch (e: Exception) { AppLog.i("!! keep-alive could not start: ${e.javaClass.simpleName}: ${e.message}"); stopSelf(); return START_NOT_STICKY }
        if (lock == null) {
            try {
                lock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "obdy:car").apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }
            } catch (_: Exception) {}
        }
        AppLog.i("[APP] keep-alive on: the app keeps running in the background while connected")
        return START_NOT_STICKY
    }

    private fun release() { try { lock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}; lock = null }
    override fun onDestroy() { release(); AppLog.i("[APP] keep-alive off"); super.onDestroy() }

    companion object {
        private const val CH = "car_link"
        private const val ID = 4107
        private const val STOP = "com.example.obdy.KEEPALIVE_STOP"
        fun start(ctx: Context) { try { ContextCompat.startForegroundService(ctx, Intent(ctx, KeepAlive::class.java)) } catch (e: Exception) { AppLog.i("!! keep-alive start refused: ${e.message}") } }
        fun stop(ctx: Context) { try { ctx.startService(Intent(ctx, KeepAlive::class.java).setAction(STOP)) } catch (_: Exception) {} }
    }
}
