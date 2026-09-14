package com.deviloufr.markettracker.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.deviloufr.markettracker.data.PriceApi
import com.deviloufr.markettracker.data.Repository
import com.deviloufr.markettracker.detection.AnomalyDetector
import com.deviloufr.markettracker.notify.Notifier
import com.deviloufr.markettracker.notify.WhatsAppSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Foreground service that polls each ticker on an interval, runs anomaly
 * detection and posts native notifications (and optional WhatsApp messages).
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val api = PriceApi()
    private val detector = AnomalyDetector()
    private val lastAlert = HashMap<String, Long>() // "SYMBOL|reason" -> wall-clock millis
    private lateinit var repo: Repository
    private var loopJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        repo = Repository.get(this)
        startForeground(NOTIF_ID, Notifier.buildMonitorNotification(this, "Starting…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (loopJob == null) {
            loopJob = scope.launch {
                repo.setMonitoring(true)
                while (isActive) {
                    try {
                        pollOnce()
                    } catch (e: Exception) {
                        // never let one bad cycle kill monitoring
                    }
                    val seconds = repo.settings.value.pollIntervalSeconds.coerceAtLeast(15)
                    delay(seconds * 1000L)
                }
            }
        }
        return START_STICKY
    }

    private suspend fun pollOnce() = coroutineScope {
        val symbols = repo.watchlist.value
        val settings = repo.settings.value
        updateMonitorNotification(symbols.size)
        val cooldownMs = settings.cooldownMinutes * 60_000L

        symbols.map { symbol ->
            async {
                val quote = api.getQuote(symbol) ?: return@async
                repo.recordQuote(quote)
                for (trigger in detector.evaluate(quote, settings)) {
                    val key = "${trigger.symbol}|${trigger.reason}"
                    val now = System.currentTimeMillis()
                    val last = lastAlert[key]
                    if (last != null && now - last < cooldownMs) continue
                    lastAlert[key] = now

                    Notifier.showAlert(this@MonitorService, trigger)
                    repo.recordAlert(trigger)
                    if (settings.whatsappEnabled) {
                        val msg = "🚨 ${trigger.symbol} — ${trigger.reason.replace('_', ' ')}\n" +
                            trigger.detail + "\n" +
                            String.format(Locale.US, "Price: %.2f", trigger.price)
                        WhatsAppSender.send(settings, msg)
                    }
                }
            }
        }.awaitAll()
    }

    private fun updateMonitorNotification(count: Int) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val text = if (count == 0) "No tickers in watchlist" else "Watching $count tickers"
        nm.notify(NOTIF_ID, Notifier.buildMonitorNotification(this, text))
    }

    override fun onDestroy() {
        loopJob?.cancel()
        scope.cancel()
        // fire-and-forget persistence on a detached scope (service scope is gone)
        CoroutineScope(Dispatchers.IO).launch { repo.setMonitoring(false) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIF_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, MonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
