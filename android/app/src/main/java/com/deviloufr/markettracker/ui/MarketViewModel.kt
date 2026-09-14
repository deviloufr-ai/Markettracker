package com.deviloufr.markettracker.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deviloufr.markettracker.data.Asset
import com.deviloufr.markettracker.data.HistoryRange
import com.deviloufr.markettracker.data.PriceApi
import com.deviloufr.markettracker.data.PricePoint
import com.deviloufr.markettracker.data.Repository
import com.deviloufr.markettracker.data.Settings
import com.deviloufr.markettracker.notify.WhatsAppSender
import com.deviloufr.markettracker.service.MonitorService
import kotlinx.coroutines.launch

class MarketViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository.get(app)
    private val api = PriceApi()

    val quotes = repo.quotes
    val watchlist = repo.watchlist
    val alerts = repo.alerts
    val settings = repo.settings
    val monitoring = repo.monitoring

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

    fun sendTestWhatsApp(onResult: (Boolean) -> Unit) = viewModelScope.launch {
        onResult(WhatsAppSender.send(settings.value, "✅ Message test MarketTracker"))
    }
}
