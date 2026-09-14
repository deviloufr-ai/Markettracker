package com.deviloufr.markettracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.deviloufr.markettracker.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Restarts monitoring after reboot if it was enabled when the device shut down. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (Repository.get(context).isMonitoringPersisted()) {
                    MonitorService.start(context)
                }
            } catch (e: Exception) {
                // ignore — user can start monitoring manually
            } finally {
                pending.finish()
            }
        }
    }
}
