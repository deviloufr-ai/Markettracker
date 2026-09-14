package com.deviloufr.markettracker.detection

import com.deviloufr.markettracker.data.AlertEvent
import com.deviloufr.markettracker.data.Quote
import com.deviloufr.markettracker.data.Settings
import java.util.Locale
import kotlin.math.abs

/**
 * Detects price moves and volume spikes. Keeps a per-symbol in-memory history so
 * moving averages / windows can be computed while monitoring runs.
 */
class AnomalyDetector {

    private data class Sample(val ts: Long, val price: Double, val volume: Double?)

    private val history = HashMap<String, ArrayDeque<Sample>>()
    private val maxSamples = 240 // ~4h at 60s cadence

    fun evaluate(quote: Quote, s: Settings): List<AlertEvent> {
        val dq = history.getOrPut(quote.symbol) { ArrayDeque() }
        val triggers = ArrayList<AlertEvent>()

        // --- price move vs the oldest sample still inside the window ---
        val windowStart = quote.ts - s.priceWindowMinutes * 60_000L
        val baseline = dq.firstOrNull { it.ts >= windowStart }?.price
        if (baseline != null && baseline != 0.0) {
            val change = (quote.price - baseline) / baseline * 100.0
            if (abs(change) >= s.priceChangePct) {
                val arrow = if (change > 0) "🔺" else "🔻" // 🔺 / 🔻
                val detail = String.format(
                    Locale.US,
                    "%s %+.2f%% over ~%dm (%.2f → %.2f)",
                    arrow, change, s.priceWindowMinutes, baseline, quote.price
                )
                triggers.add(AlertEvent(quote.symbol, "price_move", detail, quote.price, quote.ts))
            }
        }

        // --- volume spike vs the moving average of prior volumes ---
        if (quote.volume != null) {
            val priorVolumes = dq.mapNotNull { it.volume }.takeLast(s.volumeMaPeriods)
            if (priorVolumes.size >= s.volumeMaPeriods) {
                val avg = priorVolumes.average()
                if (avg > 0) {
                    val ratio = quote.volume / avg
                    if (ratio >= s.volumeSpikeFactor) {
                        val detail = String.format(
                            Locale.US,
                            "📊 volume %.1f× the %d-sample average",
                            ratio, priorVolumes.size
                        )
                        triggers.add(AlertEvent(quote.symbol, "volume_spike", detail, quote.price, quote.ts))
                    }
                }
            }
        }

        // record AFTER detection so history excludes the current tick
        dq.addLast(Sample(quote.ts, quote.price, quote.volume))
        while (dq.size > maxSamples) dq.removeFirst()

        return triggers
    }

    fun reset() = history.clear()
}
