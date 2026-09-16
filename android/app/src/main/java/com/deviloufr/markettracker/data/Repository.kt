package com.deviloufr.markettracker.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "market_tracker")

/**
 * Single source of truth shared by the UI and the monitoring service.
 * Settings / watchlist / alerts are persisted with DataStore; live quotes are
 * kept in memory.
 */
class Repository private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _quotes = MutableStateFlow<Map<String, Quote>>(emptyMap())
    val quotes: StateFlow<Map<String, Quote>> = _quotes.asStateFlow()

    val settings: StateFlow<Settings> = appContext.dataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, Settings())

    val watchlist: StateFlow<List<String>> = appContext.dataStore.data
        .map { prefs -> prefs[Keys.WATCHLIST]?.let(::parseList) ?: DEFAULT_WATCHLIST }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_WATCHLIST)

    val alerts: StateFlow<List<AlertEvent>> = appContext.dataStore.data
        .map { prefs -> prefs[Keys.ALERTS]?.let(::parseAlerts) ?: emptyList() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Recorded buy/sell transactions backing the local portfolio. */
    val trades: StateFlow<List<Trade>> = appContext.dataStore.data
        .map { prefs -> prefs[Keys.TRADES]?.let(::parseTrades) ?: emptyList() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val monitoring: StateFlow<Boolean> = appContext.dataStore.data
        .map { it[Keys.MONITORING] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Cached per-symbol deep AI analyses, persisted so they survive navigation/restart. */
    val aiAnalyses: StateFlow<Map<String, CachedAnalysis>> = appContext.dataStore.data
        .map { prefs -> prefs[Keys.AI_ANALYSES]?.let(::parseAnalyses) ?: emptyMap() }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Cached watchlist-wide AI brief, persisted so it survives navigation/restart. */
    val aiBrief: StateFlow<CachedBrief?> = appContext.dataStore.data
        .map { prefs -> prefs[Keys.AI_BRIEF]?.let(::parseBrief) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Cached forward-looking opportunities forecast, persisted across navigation/restart. */
    val aiForecast: StateFlow<CachedForecast?> = appContext.dataStore.data
        .map { prefs -> prefs[Keys.AI_FORECAST]?.let(::parseForecast) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Cached downside-risk advice for tracked assets, persisted across navigation/restart. */
    val aiRisk: StateFlow<CachedRisk?> = appContext.dataStore.data
        .map { prefs -> prefs[Keys.AI_RISK]?.let(::parseRisk) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    fun recordQuote(quote: Quote) {
        _quotes.value = _quotes.value.toMutableMap().apply { put(quote.symbol, quote) }
    }

    suspend fun addTicker(symbol: String) {
        val sym = symbol.trim().uppercase()
        if (sym.isEmpty()) return
        appContext.dataStore.edit { prefs ->
            val current = prefs[Keys.WATCHLIST]?.let(::parseList) ?: DEFAULT_WATCHLIST
            if (!current.contains(sym)) prefs[Keys.WATCHLIST] = listToStr(current + sym)
        }
    }

    suspend fun removeTicker(symbol: String) {
        appContext.dataStore.edit { prefs ->
            val current = prefs[Keys.WATCHLIST]?.let(::parseList) ?: DEFAULT_WATCHLIST
            prefs[Keys.WATCHLIST] = listToStr(current - symbol)
        }
        _quotes.value = _quotes.value.toMutableMap().apply { remove(symbol) }
    }

    suspend fun updateSettings(transform: (Settings) -> Settings) {
        appContext.dataStore.edit { prefs -> prefs.putSettings(transform(prefs.toSettings())) }
    }

    suspend fun recordAlert(event: AlertEvent) {
        appContext.dataStore.edit { prefs ->
            val current = prefs[Keys.ALERTS]?.let(::parseAlerts) ?: emptyList()
            prefs[Keys.ALERTS] = alertsToStr((listOf(event) + current).take(100))
        }
    }

    suspend fun clearAlerts() {
        appContext.dataStore.edit { it[Keys.ALERTS] = "[]" }
    }

    /** Append one trade to the portfolio (newest kept first). */
    suspend fun addTrade(trade: Trade) {
        appContext.dataStore.edit { prefs ->
            val current = prefs[Keys.TRADES]?.let(::parseTrades) ?: emptyList()
            prefs[Keys.TRADES] = tradesToStr(listOf(trade) + current)
        }
    }

    /**
     * Append several trades at once (BoursoBank import). When [replaceNotePrefix]
     * is given, existing trades whose note starts with it are dropped first — used
     * to re-import a positions snapshot without duplicating opening positions.
     */
    suspend fun addTrades(newTrades: List<Trade>, replaceNotePrefix: String? = null) {
        if (newTrades.isEmpty() && replaceNotePrefix == null) return
        appContext.dataStore.edit { prefs ->
            var current = prefs[Keys.TRADES]?.let(::parseTrades) ?: emptyList()
            if (replaceNotePrefix != null) current = current.filterNot { it.note.startsWith(replaceNotePrefix) }
            prefs[Keys.TRADES] = tradesToStr(newTrades + current)
        }
    }

    suspend fun removeTrade(id: String) {
        appContext.dataStore.edit { prefs ->
            val current = prefs[Keys.TRADES]?.let(::parseTrades) ?: emptyList()
            prefs[Keys.TRADES] = tradesToStr(current.filterNot { it.id == id })
        }
    }

    /** Persist a freshly generated per-symbol analysis, pruning entries older than 2 days. */
    suspend fun saveAnalysis(symbol: String, analysis: TrendAnalysis) {
        appContext.dataStore.edit { prefs ->
            val obj = prefs[Keys.AI_ANALYSES]?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
            val now = System.currentTimeMillis()
            val cutoff = now - 2L * 24 * 3600 * 1000
            // Drop stale entries so the blob stays small.
            obj.keys().asSequence().toList().forEach { k ->
                if ((obj.optJSONObject(k)?.optLong("ts") ?: 0L) < cutoff) obj.remove(k)
            }
            obj.put(symbol, analysisToJson(analysis, now))
            prefs[Keys.AI_ANALYSES] = obj.toString()
        }
    }

    /** Persist a freshly generated watchlist-wide brief. */
    suspend fun saveBrief(brief: MarketBrief) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.AI_BRIEF] = briefToJson(brief, System.currentTimeMillis()).toString()
        }
    }

    /** Persist a freshly generated opportunities forecast. */
    suspend fun saveForecast(forecast: ForecastAdvice) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.AI_FORECAST] = forecastToJson(forecast, System.currentTimeMillis()).toString()
        }
    }

    /** Persist a freshly generated downside-risk advice for tracked assets. */
    suspend fun saveRisk(risk: RiskAdvice) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.AI_RISK] = riskToJson(risk, System.currentTimeMillis()).toString()
        }
    }

    suspend fun setMonitoring(on: Boolean) {
        appContext.dataStore.edit { it[Keys.MONITORING] = on }
    }

    suspend fun isMonitoringPersisted(): Boolean =
        appContext.dataStore.data.map { it[Keys.MONITORING] ?: false }.first()

    companion object {
        val DEFAULT_WATCHLIST = listOf("AAPL", "MSFT", "NVDA", "TSLA", "AMZN")

        @Volatile
        private var INSTANCE: Repository? = null

        fun get(context: Context): Repository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Repository(context.applicationContext).also { INSTANCE = it }
            }
    }
}

private object Keys {
    val PRICE_PCT = doublePreferencesKey("price_pct")
    val WINDOW_MIN = intPreferencesKey("window_min")
    val VOL_FACTOR = doublePreferencesKey("vol_factor")
    val VOL_PERIODS = intPreferencesKey("vol_periods")
    val POLL_SEC = intPreferencesKey("poll_sec")
    val COOLDOWN_MIN = intPreferencesKey("cooldown_min")
    val WA_ENABLED = booleanPreferencesKey("wa_enabled")
    val WA_PHONE = stringPreferencesKey("wa_phone")
    val WA_KEY = stringPreferencesKey("wa_key")
    val AI_KEY = stringPreferencesKey("ai_key")
    val AI_MODEL = stringPreferencesKey("ai_model")
    val AI_ANALYSES = stringPreferencesKey("ai_analyses")
    val AI_BRIEF = stringPreferencesKey("ai_brief")
    val AI_FORECAST = stringPreferencesKey("ai_forecast")
    val AI_RISK = stringPreferencesKey("ai_risk")
    val WATCHLIST = stringPreferencesKey("watchlist")
    val ALERTS = stringPreferencesKey("alerts")
    val TRADES = stringPreferencesKey("trades")
    val MONITORING = booleanPreferencesKey("monitoring")
}

private fun Preferences.toSettings() = Settings(
    priceChangePct = this[Keys.PRICE_PCT] ?: 3.0,
    priceWindowMinutes = this[Keys.WINDOW_MIN] ?: 5,
    volumeSpikeFactor = this[Keys.VOL_FACTOR] ?: 3.0,
    volumeMaPeriods = this[Keys.VOL_PERIODS] ?: 20,
    pollIntervalSeconds = this[Keys.POLL_SEC] ?: 60,
    cooldownMinutes = this[Keys.COOLDOWN_MIN] ?: 15,
    whatsappEnabled = this[Keys.WA_ENABLED] ?: false,
    whatsappPhone = this[Keys.WA_PHONE] ?: "",
    whatsappApiKey = this[Keys.WA_KEY] ?: "",
    anthropicApiKey = this[Keys.AI_KEY] ?: "",
    aiModel = this[Keys.AI_MODEL] ?: AiModels.DEFAULT
)

private fun MutablePreferences.putSettings(s: Settings) {
    this[Keys.PRICE_PCT] = s.priceChangePct
    this[Keys.WINDOW_MIN] = s.priceWindowMinutes
    this[Keys.VOL_FACTOR] = s.volumeSpikeFactor
    this[Keys.VOL_PERIODS] = s.volumeMaPeriods
    this[Keys.POLL_SEC] = s.pollIntervalSeconds
    this[Keys.COOLDOWN_MIN] = s.cooldownMinutes
    this[Keys.WA_ENABLED] = s.whatsappEnabled
    this[Keys.WA_PHONE] = s.whatsappPhone
    this[Keys.WA_KEY] = s.whatsappApiKey
    this[Keys.AI_KEY] = s.anthropicApiKey
    this[Keys.AI_MODEL] = s.aiModel
}

private fun parseList(s: String): List<String> =
    if (s.isBlank()) emptyList()
    else s.split(",").map { it.trim() }.filter { it.isNotEmpty() }

private fun listToStr(list: List<String>): String = list.joinToString(",")

private fun parseAlerts(s: String): List<AlertEvent> = try {
    val arr = JSONArray(s)
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        AlertEvent(
            symbol = o.getString("symbol"),
            reason = o.getString("reason"),
            detail = o.getString("detail"),
            price = o.getDouble("price"),
            ts = o.getLong("ts")
        )
    }
} catch (e: Exception) {
    emptyList()
}

private fun alertsToStr(list: List<AlertEvent>): String {
    val arr = JSONArray()
    list.forEach { e ->
        arr.put(
            JSONObject().apply {
                put("symbol", e.symbol)
                put("reason", e.reason)
                put("detail", e.detail)
                put("price", e.price)
                put("ts", e.ts)
            }
        )
    }
    return arr.toString()
}

private fun parseTrades(s: String): List<Trade> = try {
    val arr = JSONArray(s)
    (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val symbol = o.optString("symbol").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        Trade(
            id = o.optString("id").takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
            symbol = symbol,
            side = if (o.optString("side") == "SELL") TradeSide.SELL else TradeSide.BUY,
            quantity = o.optDouble("quantity", 0.0),
            price = o.optDouble("price", 0.0),
            ts = o.optLong("ts"),
            fees = o.optDouble("fees", 0.0),
            note = o.optString("note")
        )
    }
} catch (e: Exception) {
    emptyList()
}

private fun tradesToStr(list: List<Trade>): String {
    val arr = JSONArray()
    list.forEach { t ->
        arr.put(
            JSONObject().apply {
                put("id", t.id)
                put("symbol", t.symbol)
                put("side", t.side.name)
                put("quantity", t.quantity)
                put("price", t.price)
                put("ts", t.ts)
                put("fees", t.fees)
                put("note", t.note)
            }
        )
    }
    return arr.toString()
}

// --- AI analysis / brief caching (de)serialization ------------------------------------------

private fun strArray(list: List<String>): JSONArray = JSONArray().apply { list.forEach { put(it) } }

private fun parseStrList(arr: JSONArray?): List<String> {
    arr ?: return emptyList()
    return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
}

private fun sourcesToJson(list: List<AiSource>): JSONArray = JSONArray().apply {
    list.forEach { put(JSONObject().put("title", it.title).put("url", it.url)) }
}

private fun parseSources(arr: JSONArray?): List<AiSource> {
    arr ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        arr.optJSONObject(i)?.let { AiSource(it.optString("title"), it.optString("url")) }
    }
}

private fun analysisToJson(a: TrendAnalysis, ts: Long): JSONObject = JSONObject().apply {
    put("ts", ts)
    put("direction", a.direction)
    put("confidence", a.confidence)
    put("summary", a.summary)
    put("drivers", strArray(a.drivers))
    put("horizon", a.horizon)
    put("sources", sourcesToJson(a.sources))
}

private fun parseAnalyses(s: String): Map<String, CachedAnalysis> = try {
    val obj = JSONObject(s)
    val out = LinkedHashMap<String, CachedAnalysis>()
    obj.keys().forEach { key ->
        val o = obj.optJSONObject(key) ?: return@forEach
        out[key] = CachedAnalysis(
            analysis = TrendAnalysis(
                direction = o.optString("direction", "neutre"),
                confidence = o.optInt("confidence"),
                summary = o.optString("summary"),
                drivers = parseStrList(o.optJSONArray("drivers")),
                horizon = o.optString("horizon"),
                sources = parseSources(o.optJSONArray("sources"))
            ),
            ts = o.optLong("ts")
        )
    }
    out
} catch (e: Exception) {
    emptyMap()
}

private fun moversToJson(list: List<Mover>): JSONArray = JSONArray().apply {
    list.forEach { m ->
        put(JSONObject().apply {
            put("symbol", m.symbol)
            put("name", m.name)
            put("changePct", m.changePct ?: JSONObject.NULL)
            put("note", m.note)
        })
    }
}

private fun parseMovers(arr: JSONArray?): List<Mover> {
    arr ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        arr.optJSONObject(i)?.let { o ->
            val symbol = o.optString("symbol")
            if (symbol.isBlank()) null
            else Mover(
                symbol = symbol,
                name = o.optString("name"),
                changePct = if (o.has("changePct") && !o.isNull("changePct")) o.optDouble("changePct") else null,
                note = o.optString("note")
            )
        }
    }
}

private fun briefToJson(b: MarketBrief, ts: Long): JSONObject = JSONObject().apply {
    put("ts", ts)
    put("sentiment", b.sentiment)
    put("summary", b.summary)
    put("highlights", strArray(b.highlights))
    put("movers", moversToJson(b.movers))
    put("sources", sourcesToJson(b.sources))
}

private fun parseBrief(s: String): CachedBrief? = try {
    val o = JSONObject(s)
    CachedBrief(
        brief = MarketBrief(
            sentiment = o.optString("sentiment", "mitigé"),
            summary = o.optString("summary"),
            highlights = parseStrList(o.optJSONArray("highlights")),
            movers = parseMovers(o.optJSONArray("movers")),
            sources = parseSources(o.optJSONArray("sources"))
        ),
        ts = o.optLong("ts")
    )
} catch (e: Exception) {
    null
}

private fun opportunitiesToJson(list: List<Opportunity>): JSONArray = JSONArray().apply {
    list.forEach { op ->
        put(JSONObject().apply {
            put("symbol", op.symbol)
            put("name", op.name)
            put("potentialPct", op.potentialPct ?: JSONObject.NULL)
            put("rationale", op.rationale)
        })
    }
}

private fun parseOpportunities(arr: JSONArray?): List<Opportunity> {
    arr ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        arr.optJSONObject(i)?.let { o ->
            val symbol = o.optString("symbol")
            if (symbol.isBlank()) null
            else Opportunity(
                symbol = symbol,
                name = o.optString("name"),
                potentialPct = if (o.has("potentialPct") && !o.isNull("potentialPct")) o.optDouble("potentialPct") else null,
                rationale = o.optString("rationale")
            )
        }
    }
}

private fun forecastToJson(f: ForecastAdvice, ts: Long): JSONObject = JSONObject().apply {
    put("ts", ts)
    put("horizons", JSONArray().apply {
        f.horizons.forEach { h ->
            put(JSONObject()
                .put("label", h.label)
                .put("opportunities", opportunitiesToJson(h.opportunities)))
        }
    })
    put("sources", sourcesToJson(f.sources))
}

private fun parseForecast(s: String): CachedForecast? = try {
    val o = JSONObject(s)
    val arr = o.optJSONArray("horizons")
    val horizons = if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
        arr.optJSONObject(i)?.let { h ->
            val label = h.optString("label")
            if (label.isBlank()) null
            else HorizonForecast(label, parseOpportunities(h.optJSONArray("opportunities")))
        }
    }
    CachedForecast(
        forecast = ForecastAdvice(horizons = horizons, sources = parseSources(o.optJSONArray("sources"))),
        ts = o.optLong("ts")
    )
} catch (e: Exception) {
    null
}

private fun riskToJson(r: RiskAdvice, ts: Long): JSONObject = JSONObject().apply {
    put("ts", ts)
    put("risks", JSONArray().apply {
        r.warnings.forEach { w ->
            put(JSONObject().apply {
                put("symbol", w.symbol)
                put("name", w.name)
                put("downsidePct", w.downsidePct ?: JSONObject.NULL)
                put("severity", w.severity)
                put("rationale", w.rationale)
            })
        }
    })
    put("sources", sourcesToJson(r.sources))
}

private fun parseRisk(s: String): CachedRisk? = try {
    val o = JSONObject(s)
    val arr = o.optJSONArray("risks")
    val warnings = if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
        arr.optJSONObject(i)?.let { w ->
            val symbol = w.optString("symbol")
            if (symbol.isBlank()) null
            else RiskWarning(
                symbol = symbol,
                name = w.optString("name"),
                downsidePct = if (w.has("downsidePct") && !w.isNull("downsidePct")) w.optDouble("downsidePct") else null,
                severity = w.optString("severity").ifBlank { "élevé" },
                rationale = w.optString("rationale")
            )
        }
    }
    CachedRisk(
        risk = RiskAdvice(warnings = warnings, sources = parseSources(o.optJSONArray("sources"))),
        ts = o.optLong("ts")
    )
} catch (e: Exception) {
    null
}
