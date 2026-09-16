package com.deviloufr.markettracker.data

/** A single price observation for a symbol. */
data class Quote(
    val symbol: String,
    val price: Double,
    val volume: Double?,
    val previousClose: Double?,
    val ts: Long, // epoch millis
    val name: String? = null,     // company/instrument name from Yahoo, when available
    val exchange: String? = null  // exchange name from Yahoo (e.g. "NasdaqGS", "Paris")
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

/**
 * A speculative opportunity proposed by the AI forecast for a given horizon.
 * [potentialPct] is an estimated potential upside %, when the model gives one.
 */
data class Opportunity(
    val symbol: String,
    val name: String,
    val potentialPct: Double?,
    val rationale: String
)

/** Opportunities proposed for one time horizon (e.g. "1 semaine"). */
data class HorizonForecast(val label: String, val opportunities: List<Opportunity>)

/**
 * A downside-risk warning for a **tracked** asset: an asset in the user's watchlist that
 * carries a high risk of decline. [downsidePct] is an estimated potential drop % (negative,
 * e.g. -12.0), when the model gives one. [severity] is a short French level
 * ("élevé" | "modéré" | "faible") used to color/label the warning.
 */
data class RiskWarning(
    val symbol: String,
    val name: String,
    val downsidePct: Double?,
    val severity: String,
    val rationale: String
)

/**
 * Downside-risk advice: the tracked assets with the highest risk of decrease, from market
 * analytics + news. Speculative, informational only — not financial advice.
 */
data class RiskAdvice(
    val warnings: List<RiskWarning>,
    val sources: List<AiSource>
)

/**
 * Forward-looking "Opportunités" advice: the assets with the highest potential
 * increase per horizon, from market analytics + news. Speculative, not advice.
 */
data class ForecastAdvice(
    val horizons: List<HorizonForecast>,
    val sources: List<AiSource>
) {
    companion object {
        /** The horizons requested, in display order. */
        val HORIZONS = listOf("1 semaine", "1 mois", "6 mois", "1 an")
    }
}

// --- Portfolio (local buy/sell tracking) ----------------------------------------------------

/** Side of a recorded trade. */
enum class TradeSide { BUY, SELL }

/** French label for a [TradeSide], for display in the UI. */
fun sideLabelFr(side: TradeSide): String = when (side) {
    TradeSide.BUY -> "Achat"
    TradeSide.SELL -> "Vente"
}

/**
 * A single buy/sell transaction the user recorded (or imported from a BoursoBank
 * export). [price] and [fees] are in the asset's own trading currency; [symbol]
 * follows Yahoo conventions so it matches quotes and the watchlist.
 */
data class Trade(
    val id: String,
    val symbol: String,
    val side: TradeSide,
    val quantity: Double,
    val price: Double,       // per-unit executed price
    val ts: Long,            // epoch millis of the trade
    val fees: Double = 0.0,
    val note: String = ""
)

/**
 * Net holding for one symbol, derived from its [Trade]s with the average-cost
 * method. [quantity] is 0 for a fully-closed position (kept only for its
 * [realizedPnl]). P&L helpers take the current [Quote] price as an argument so
 * the value is always live.
 */
data class Position(
    val symbol: String,
    val quantity: Double,
    val avgCost: Double,     // weighted cost of the units still held
    val realizedPnl: Double, // booked P&L from past sells
    val invested: Double     // cost basis of the open position (quantity * avgCost)
) {
    val isOpen: Boolean get() = quantity > 1e-9
    fun marketValue(price: Double): Double = quantity * price
    fun unrealizedPnl(price: Double): Double = (price - avgCost) * quantity
    fun unrealizedPct(price: Double): Double? =
        avgCost.takeIf { it > 0.0 }?.let { (price - it) / it * 100.0 }
}

/** A per-symbol deep analysis kept on device with the time it was generated. */
data class CachedAnalysis(val analysis: TrendAnalysis, val ts: Long)

/** The watchlist-wide brief kept on device with the time it was generated. */
data class CachedBrief(val brief: MarketBrief, val ts: Long)

/** The forward-looking opportunities forecast kept on device with its generation time. */
data class CachedForecast(val forecast: ForecastAdvice, val ts: Long)

/** The downside-risk advice for tracked assets kept on device with its generation time. */
data class CachedRisk(val risk: RiskAdvice, val ts: Long)
