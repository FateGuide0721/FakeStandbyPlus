package com.example.service

import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.example.adb.AdbManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class FakeStandbyTileService : TileService() {
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var stateObserverJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        AdbManager.getInstance(this).ensureConnected()
        stateObserverJob?.cancel()
        stateObserverJob = serviceScope.launch {
            FakeStandbyService.isStandbyActive.collect { isActive ->
                updateTileState(isActive)
            }
        }
    }

    override fun onStopListening() {
        stateObserverJob?.cancel()
        stateObserverJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先在 FakeStandby 中开启悬浮窗权限", Toast.LENGTH_SHORT).show()
            return
        }

        if (FakeStandbyService.isStandbyActive.value) {
            FakeStandbyService.stop(this)
            updateTileState(false)
        } else {
            // Execute: adb shell cmd statusbar collapse
            AdbManager.getInstance(this).executeCollapseStatusBar()

            // Start foreground overlay service directly over the existing screen
            FakeStandbyService.start(this)
            updateTileState(true)
        }
    }

    private fun updateTileState(isActive: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isActive) "已全屏息屏" else "点击开启"
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        stateObserverJob?.cancel()
        super.onDestroy()
    }
}
