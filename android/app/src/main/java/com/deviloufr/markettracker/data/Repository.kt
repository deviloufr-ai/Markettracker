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

    val monitoring: StateFlow<Boolean> = appContext.dataStore.data
        .map { it[Keys.MONITORING] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

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
    val WATCHLIST = stringPreferencesKey("watchlist")
    val ALERTS = stringPreferencesKey("alerts")
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
    whatsappApiKey = this[Keys.WA_KEY] ?: ""
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
