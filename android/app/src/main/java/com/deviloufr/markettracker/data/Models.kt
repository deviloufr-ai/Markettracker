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

/** A single historical close point, used to draw the price history chart. */
data class PricePoint(
    val ts: Long,     // epoch millis
    val close: Double
)

/**
 * A tradable asset offered in the "add assets" picker: a Yahoo symbol plus a
 * human-readable name. [group] is an optional sub-section label used only for
 * large categories (e.g. "Tech US", "France (CAC 40)" within Actions); the
 * market itself is derived from [symbol] via `marketOf`. Also used to carry
 * live Yahoo search results, which have no group.
 */
data class Asset(
    val symbol: String,
    val name: String,
    val group: String? = null
)

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
    val whatsappApiKey: String = "",
    /** Anthropic API key for the optional "Analyse IA approfondie". Empty = feature off. */
    val anthropicApiKey: String = "",
    /** Claude model used for the deep analysis. Both defaults support the web_search tool. */
    val aiModel: String = AiModels.DEFAULT
)

/** Claude models offered for the deep AI analysis (both support `web_search_20260209`). */
object AiModels {
    const val OPUS = "claude-opus-5"
    const val SONNET = "claude-sonnet-5"
    const val DEFAULT = OPUS

    /** French display label for a model id, for the settings selector. */
    fun label(id: String): String = when (id) {
        OPUS -> "Opus 5 (qualité max)"
        SONNET -> "Sonnet 5 (rapide, moins cher)"
        else -> id
    }
}

/** Directional read of the on-device technical analysis. */
enum class TrendVerdict { UP, DOWN, NEUTRAL }

/** French label for a [TrendVerdict], for display in the UI. */
fun verdictLabelFr(v: TrendVerdict): String = when (v) {
    TrendVerdict.UP -> "Tendance haussière"
    TrendVerdict.DOWN -> "Tendance baissière"
    TrendVerdict.NEUTRAL -> "Tendance neutre"
}

/**
 * Result of the on-device (network-free) technical analysis, computed from the
 * price history already fetched for the detail chart.
 */
data class TechnicalSignals(
    val verdict: TrendVerdict,
    val confidence: Int,          // 0..100
    val momentumPct: Double,      // change over the analysed window, %
    val rsi: Double?,             // Wilder RSI(14), null if not enough data
    val trendLabel: String,       // e.g. "SMA courte > SMA longue"
    val volatilityPct: Double,    // stdev of period returns, %
    val rangePositionPct: Double, // 0 = at the low, 100 = at the high of the window
    val rationale: List<String>   // short French bullet points
)

/** A web source Claude cited during the deep analysis. */
data class AiSource(val title: String, val url: String)

/**
 * Structured result of the optional Claude deep analysis (with web search).
 * [direction] is one of "hausse" | "baisse" | "neutre".
 */
data class TrendAnalysis(
    val direction: String,
    val confidence: Int,          // 0..100
    val summary: String,
    val drivers: List<String>,
    val horizon: String,
    val sources: List<AiSource>
)

/**
 * A notable market mover surfaced by the AI brief. May or may not be in the
 * user's watchlist ([symbol] is checked against the watchlist at render time).
 */
data class Mover(
    val symbol: String,
    val name: String,
    val changePct: Double?, // day change %, when the model reports it
    val note: String        // short plain-language reason
)

/**
 * Structured result of the optional watchlist-wide Claude "market brief".
 * [sentiment] is one of "haussier" | "baissier" | "mitigé".
 */
data class MarketBrief(
    val sentiment: String,
    val summary: String,
    val highlights: List<String>,
    val movers: List<Mover>,
    val sources: List<AiSource>
)

/** A per-symbol deep analysis kept on device with the time it was generated. */
data class CachedAnalysis(val analysis: TrendAnalysis, val ts: Long)

/** The watchlist-wide brief kept on device with the time it was generated. */
data class CachedBrief(val brief: MarketBrief, val ts: Long)
