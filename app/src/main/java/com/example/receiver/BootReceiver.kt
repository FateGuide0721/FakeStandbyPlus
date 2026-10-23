package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.adb.AdbManager
import com.example.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BootReceiver:
 * Listens for system boot completion (ACTION_BOOT_COMPLETED, QUICKBOOT_POWERON).
 * Automatically acquires Wireless ADB authorization using the saved persistent key,
 * ensuring semi-permanent ADB access without needing to re-pair on every boot.
 */
class BootReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON") {
            Log.i("BootReceiver", "Boot completed detected. Initializing ADB auto-reconnect...")
            val adbManager = AdbManager.getInstance(context)
            adbManager.startConnectDiscovery()
            scope.launch {
                val result = adbManager.autoAuthorize()
                Log.i("BootReceiver", "Boot auto-authorization result: ${result.getOrNull() ?: result.exceptionOrNull()?.message}")
            }

            val prefs = PreferencesManager.getInstance(context)
            if (prefs.config.value.autoStartOnBoot) {
                Log.i("BootReceiver", "Auto-start is enabled, readying FakeStandby")
            }
        }
    }
}
