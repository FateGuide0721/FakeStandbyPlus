package com.example.service

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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.MainActivity
import com.example.R
import com.example.adb.AdbManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AdbPairingService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var adbManager: AdbManager
    private var currentPort: Int = -1

    override fun onCreate() {
        super.onCreate()
        adbManager = AdbManager.getInstance(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                adbManager.stopPairingDiscovery()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_REPLY -> {
                val results = RemoteInput.getResultsFromIntent(intent)
                val code = results?.getCharSequence(KEY_PAIRING_CODE)?.toString().orEmpty()
                val port = intent.getIntExtra(EXTRA_PORT, currentPort)
                if (code.isNotBlank() && port > 0) {
                    handlePairingInput(port, code)
                }
            }

            ACTION_START -> {
                startPairingFlow()
            }

            else -> {
                startPairingFlow()
            }
        }
        return START_NOT_STICKY
    }

    private fun startPairingFlow() {
        val initialNotification = buildSearchingNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground error", e)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, initialNotification)
        }

        // Start mDNS search
        adbManager.startPairingDiscovery { port ->
            currentPort = port
            val foundNotification = buildFoundNotification(port)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, foundNotification)
        }
    }

    private fun handlePairingInput(port: Int, code: String) {
        val workingNotification = buildWorkingNotification()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, workingNotification)

        scope.launch {
            val result = adbManager.pair(port, code)
            if (result.isSuccess) {
                val successNotification = buildSuccessNotification()
                nm.notify(NOTIFICATION_ID, successNotification)
                delay(3000)
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            } else {
                val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "配对失败"
                val failNotification = buildFailureNotification(port, errorMsg)
                nm.notify(NOTIFICATION_ID, failNotification)
            }
        }
    }

    private fun buildSearchingNotification(): Notification {
        val stopIntent = Intent(this, AdbPairingService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this,
            201,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val appPending = PendingIntent.getActivity(
            this,
            202,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_fakestandby)
            .setContentTitle(getString(R.string.notification_adb_pairing_searching_title))
            .setContentText(getString(R.string.notification_adb_pairing_searching_text))
            .setContentIntent(appPending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_adb_pairing_action_stop),
                stopPending
            )
            .build()
    }

    private fun buildFoundNotification(port: Int): Notification {
        val remoteInput = RemoteInput.Builder(KEY_PAIRING_CODE)
            .setLabel("输入6位配对码")
            .build()

        val replyIntent = Intent(this, AdbPairingService::class.java).apply {
            action = ACTION_REPLY
            putExtra(EXTRA_PORT, port)
        }
        val replyPending = PendingIntent.getService(
            this,
            203,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            getString(R.string.notification_adb_pairing_action_input),
            replyPending
        ).addRemoteInput(remoteInput).build()

        val stopIntent = Intent(this, AdbPairingService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this,
            204,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val appPending = PendingIntent.getActivity(
            this,
            205,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_fakestandby)
            .setContentTitle(getString(R.string.notification_adb_pairing_found_title))
            .setContentText(getString(R.string.notification_adb_pairing_found_text, port))
            .setContentIntent(appPending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(replyAction)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_adb_pairing_action_stop),
                stopPending
            )
            .build()
    }

    private fun buildWorkingNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_fakestandby)
            .setContentTitle(getString(R.string.notification_adb_pairing_working_title))
            .setContentText("正在通过 SPAKE2 握手建立连接…")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun buildSuccessNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_fakestandby)
            .setContentTitle(getString(R.string.notification_adb_pairing_succeed_title))
            .setContentText(getString(R.string.notification_adb_pairing_succeed_text))
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun buildFailureNotification(port: Int, reason: String): Notification {
        val retryRemoteInput = RemoteInput.Builder(KEY_PAIRING_CODE)
            .setLabel("重新输入6位配对码")
            .build()

        val retryIntent = Intent(this, AdbPairingService::class.java).apply {
            action = ACTION_REPLY
            putExtra(EXTRA_PORT, port)
        }
        val retryPending = PendingIntent.getService(
            this,
            206,
            retryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val retryAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_rotate,
            "重新输入配对码",
            retryPending
        ).addRemoteInput(retryRemoteInput).build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_fakestandby)
            .setContentTitle(getString(R.string.notification_adb_pairing_failed_title))
            .setContentText("失败原因: $reason。请点击重试")
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(retryAction)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_adb_pairing),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "无线调试配对自动搜索与验证回复通知"
            setShowBadge(true)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        adbManager.stopPairingDiscovery()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AdbPairingService"
        const val ACTION_START = "com.example.action.START_PAIRING_SEARCH"
        const val ACTION_STOP = "com.example.action.STOP_PAIRING_SEARCH"
        const val ACTION_REPLY = "com.example.action.PAIRING_REPLY"

        const val EXTRA_PORT = "extra_port"
        const val KEY_PAIRING_CODE = "key_pairing_code"
        private const val NOTIFICATION_ID = 20262
        private const val CHANNEL_ID = "fake_standby_adb_pairing_channel"

        fun startSearch(context: Context) {
            val intent = Intent(context, AdbPairingService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopSearch(context: Context) {
            val intent = Intent(context, AdbPairingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
