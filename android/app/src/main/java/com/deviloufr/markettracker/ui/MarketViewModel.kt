package com.deviloufr.markettracker.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deviloufr.markettracker.data.AiTrendApi
import com.deviloufr.markettracker.data.Asset
import com.deviloufr.markettracker.data.ForecastAdvice
import com.deviloufr.markettracker.data.HistoryRange
import com.deviloufr.markettracker.data.MarketBrief
import com.deviloufr.markettracker.data.PriceApi
import com.deviloufr.markettracker.data.PricePoint
import com.deviloufr.markettracker.data.Quote
import com.deviloufr.markettracker.data.Repository
import com.deviloufr.markettracker.data.Settings
import com.deviloufr.markettracker.data.TechnicalAnalysis
import com.deviloufr.markettracker.data.TechnicalSignals
import com.deviloufr.markettracker.data.TrendAnalysis
import com.deviloufr.markettracker.notify.WhatsAppSender
import com.deviloufr.markettracker.service.MonitorService
import kotlinx.coroutines.launch

class MarketViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository.get(app)
    private val api = PriceApi()
    private val aiApi = AiTrendApi()

    val quotes = repo.quotes
    val watchlist = repo.watchlist
    val alerts = repo.alerts
    val settings = repo.settings
    val monitoring = repo.monitoring
    val aiAnalyses = repo.aiAnalyses
    val aiBrief = repo.aiBrief
    val aiForecast = repo.aiForecast

    fun addTicker(symbol: String) = viewModelScope.launch { repo.addTicker(symbol) }
    fun removeTicker(symbol: String) = viewModelScope.launch { repo.removeTicker(symbol) }
    fun updateSettings(transform: (Settings) -> Settings) =
        viewModelScope.launch { repo.updateSettings(transform) }
    fun clearAlerts() = viewModelScope.launch { repo.clearAlerts() }

    fun startMonitoring(context: Context) = MonitorService.start(context)
    fun stopMonitoring(context: Context) = MonitorService.stop(context)

    /** One-off refresh for the UI (does not fire alerts). */
    fun refreshOnce() = viewModelScope.launch {
        watchlist.value.forEach { symbol ->
            launch { api.getQuote(symbol)?.let { repo.recordQuote(it) } }
        }
    }

    /** Fetch historical closes for the detail chart. Empty list = no data/error. */
    suspend fun fetchHistory(symbol: String, range: HistoryRange): List<PricePoint> =
        api.getHistory(symbol, range)

    /** Live Yahoo symbol search for the asset picker. Empty list = no match/error. */
    suspend fun searchAssets(query: String): List<Asset> = api.searchSymbols(query)

    /** Pure, on-device technical read of a symbol from its history + live quote (no network). */
    fun computeSignals(points: List<PricePoint>, quote: Quote?): TechnicalSignals =
        TechnicalAnalysis.analyze(points, quote)

    /** True once the user has entered an Anthropic key, i.e. the deep AI analysis is available. */
    fun aiEnabled(): Boolean = settings.value.anthropicApiKey.isNotBlank()

    /**
     * Optional Claude deep analysis (with web search) for one symbol. The previously cached
     * analysis (if any) is fed back so the model can build on it and note what changed.
     */
    suspend fun analyzeWithAi(
        symbol: String,
        quote: Quote?,
        signals: TechnicalSignals
    ): Result<TrendAnalysis> {
        val s = settings.value
        val prev = aiAnalyses.value[symbol]
        return aiApi.analyze(
            symbol, quote, signals, s.anthropicApiKey, s.aiModel,
            previous = prev?.analysis, previousTs = prev?.ts
        ).onSuccess { repo.saveAnalysis(symbol, it) }
    }

    /** Optional Claude watchlist-wide market brief (with web search), fed the previous brief. */
    suspend fun marketBrief(): Result<MarketBrief> {
        val s = settings.value
        val prev = aiBrief.value
        return aiApi.brief(
            watchlist.value, quotes.value, s.anthropicApiKey, s.aiModel,
            previous = prev?.brief, previousTs = prev?.ts
        ).onSuccess { repo.saveBrief(it) }
    }

    /** Optional Claude forward-looking opportunities forecast, fed the previous forecast. */
    suspend fun forecastAdvice(): Result<ForecastAdvice> {
        val s = settings.value
        val prev = aiForecast.value
        return aiApi.forecast(
            watchlist.value, quotes.value, s.anthropicApiKey, s.aiModel,
            previous = prev?.forecast, previousTs = prev?.ts
        ).onSuccess { repo.saveForecast(it) }
    }

    fun sendTestWhatsApp(onResult: (Boolean) -> Unit) = viewModelScope.launch {
        onResult(WhatsAppSender.send(settings.value, "✅ Message test MarketTracker"))
    }
}
