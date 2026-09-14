package com.deviloufr.markettracker.data

/** A single price observation for a symbol. */
data class Quote(
    val symbol: String,
    val price: Double,
    val volume: Double?,
    val previousClose: Double?,
    val ts: Long // epoch millis
) {
    /** Percentage change vs the previous close (the "day change"), if known. */
    val dayChangePct: Double?
        get() = previousClose?.takeIf { it != 0.0 }?.let { (price - it) / it * 100.0 }
}

/** A fired alert, shown in the feed and (optionally) sent to WhatsApp. */
data class AlertEvent(
    val symbol: String,
    val reason: String,   // "price_move" | "volume_spike"
    val detail: String,
    val price: Double,
    val ts: Long
)

/** French label for an alert [AlertEvent.reason] key, for display in the UI/notifications. */
fun reasonLabelFr(reason: String): String = when (reason) {
    "price_move" -> "variation de prix"
    "volume_spike" -> "pic de volume"
    else -> reason.replace('_', ' ')
}

/** User-tunable monitoring settings. */
data class Settings(
    val priceChangePct: Double = 3.0,
    val priceWindowMinutes: Int = 5,
    val volumeSpikeFactor: Double = 3.0,
    val volumeMaPeriods: Int = 20,
    val pollIntervalSeconds: Int = 60,
    val cooldownMinutes: Int = 15,
    val whatsappEnabled: Boolean = false,
    val whatsappPhone: String = "",
    val whatsappApiKey: String = ""
)
