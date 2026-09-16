package com.deviloufr.markettracker.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deviloufr.markettracker.data.AiTrendApi
import com.deviloufr.markettracker.data.Asset
import com.deviloufr.markettracker.data.BoursoCsvParser
import com.deviloufr.markettracker.data.ForecastAdvice
import com.deviloufr.markettracker.data.HistoryRange
import com.deviloufr.markettracker.data.MarketBrief
import com.deviloufr.markettracker.data.PortfolioMath
import com.deviloufr.markettracker.data.PriceApi
import com.deviloufr.markettracker.data.PricePoint
import com.deviloufr.markettracker.data.Quote
import com.deviloufr.markettracker.data.Repository
import com.deviloufr.markettracker.data.RiskAdvice
import com.deviloufr.markettracker.data.Settings
import com.deviloufr.markettracker.data.TechnicalAnalysis
import com.deviloufr.markettracker.data.TechnicalSignals
import com.deviloufr.markettracker.data.Trade
import com.deviloufr.markettracker.data.TrendAnalysis
import com.deviloufr.markettracker.data.XlsxReader
import com.deviloufr.markettracker.BuildConfig
import com.deviloufr.markettracker.data.UpdateApi
import com.deviloufr.markettracker.notify.WhatsAppSender
import com.deviloufr.markettracker.service.MonitorService
import com.deviloufr.markettracker.update.UpdateInstaller
import com.deviloufr.markettracker.update.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MarketViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository.get(app)
    private val api = PriceApi()
    private val aiApi = AiTrendApi()
    private val updateApi = UpdateApi()

    val quotes = repo.quotes
    val watchlist = repo.watchlist
    val alerts = repo.alerts
    val trades = repo.trades
    val settings = repo.settings
    val monitoring = repo.monitoring
    val aiAnalyses = repo.aiAnalyses
    val aiBrief = repo.aiBrief
    val aiForecast = repo.aiForecast
    val aiRisk = repo.aiRisk

    /** Per-symbol intraday series driving the watchlist row sparklines. */
    private val _sparklines = MutableStateFlow<Map<String, List<PricePoint>>>(emptyMap())
    val sparklines = _sparklines.asStateFlow()

    // --- App updates -------------------------------------------------------------------------

    /** Current in-app updater state (drives the update banner). */
    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update = _update.asStateFlow()

    /** True once the user closes the banner for the current build (until a newer one appears). */
    private val _updateDismissed = MutableStateFlow(false)
    val updateDismissed = _updateDismissed.asStateFlow()

    private fun updateBusy(): Boolean =
        _update.value.let { it is UpdateState.Downloading || it is UpdateState.ReadyToInstall }

    /**
     * Check the public releases channel for a newer build. Runs once on launch and
     * on demand from Settings. [onResult] (optional) gets a user-facing message so
     * the manual check can show a toast. An in-flight download/install is left
     * untouched.
     */
    fun checkForUpdate(onResult: ((String) -> Unit)? = null) = viewModelScope.launch {
        val busy = updateBusy()
        val latest = updateApi.fetchLatest()
        when {
            latest == null ->
                onResult?.invoke("Vérification impossible. Réessayez plus tard.")
            latest.versionCode > BuildConfig.VERSION_CODE -> {
                if (!busy) {
                    _updateDismissed.value = false
                    _update.value = UpdateState.Available(latest)
                }
                onResult?.invoke("Nouvelle version disponible : ${latest.versionName}.")
            }
            else -> {
                if (!busy && _update.value is UpdateState.Available) _update.value = UpdateState.Idle
                onResult?.invoke("Application à jour (v${BuildConfig.VERSION_NAME}).")
            }
        }
    }

    /** Hide the update banner for this build (a newer build re-shows it). */
    fun dismissUpdate() { _updateDismissed.value = true }

    /** Download the available (or previously failed) build's APK, then launch the installer. */
    fun downloadUpdate() {
        val release = (_update.value as? UpdateState.Available)?.release
            ?: (_update.value as? UpdateState.Failed)?.release
            ?: return
        val ctx = getApplication<Application>()
        _updateDismissed.value = false
        _update.value = UpdateState.Downloading(release, -1f)
        viewModelScope.launch {
            val id = UpdateInstaller.enqueue(ctx, release)
            if (id == null) {
                _update.value = UpdateState.Failed(release, "Échec du téléchargement.")
                return@launch
            }
            while (true) {
                val s = UpdateInstaller.query(ctx, id)
                when {
                    s.failed -> {
                        _update.value = UpdateState.Failed(release, "Échec du téléchargement.")
                        break
                    }
                    s.done && s.fileUri != null -> {
                        _update.value = UpdateState.ReadyToInstall(release, s.fileUri)
                        UpdateInstaller.install(ctx, s.fileUri)
                        break
                    }
                    else -> {
                        _update.value = UpdateState.Downloading(release, s.progress)
                        delay(500)
                    }
                }
            }
        }
    }

    /** Re-launch the installer for an already-downloaded build (e.g. after granting the permission). */
    fun installReady() {
        val st = _update.value as? UpdateState.ReadyToInstall ?: return
        UpdateInstaller.install(getApplication<Application>(), st.fileUri)
    }

    fun addTicker(symbol: String) = viewModelScope.launch { repo.addTicker(symbol) }
    fun removeTicker(symbol: String) = viewModelScope.launch { repo.removeTicker(symbol) }

    // --- Portfolio ---------------------------------------------------------------------------

    /** Record a buy/sell, then pull a fresh quote so its live P&L shows immediately. */
    fun addTrade(trade: Trade) = viewModelScope.launch {
        repo.addTrade(trade)
        api.getQuote(trade.symbol)?.let { repo.recordQuote(it) }
    }

    fun removeTrade(id: String) = viewModelScope.launch { repo.removeTrade(id) }

    /** Symbols with an open (non-zero) position, derived from the recorded trades. */
    fun heldSymbols(): List<String> =
        PortfolioMath.positionsFrom(trades.value).filter { it.isOpen }.map { it.symbol }

    /** Fetch quotes for the held symbols so the portfolio shows live values. */
    fun refreshPortfolioQuotes(symbols: List<String> = heldSymbols()) = viewModelScope.launch {
        symbols.distinct().forEach { s -> launch { api.getQuote(s)?.let { repo.recordQuote(it) } } }
    }

    /**
     * Commit reviewed import rows and warm their quotes. A positions [snapshot]
     * replaces the previous snapshot (so re-importing updates rather than
     * duplicates); an operations history is appended.
     */
    fun importTrades(newTrades: List<Trade>, snapshot: Boolean) = viewModelScope.launch {
        repo.addTrades(newTrades, replaceNotePrefix = if (snapshot) BoursoCsvParser.SNAPSHOT_NOTE else null)
        refreshPortfolioQuotes(newTrades.map { it.symbol })
    }

    /**
     * Read and parse a BoursoBank export at [uri] — CSV or XLSX (positions
     * snapshot or operations history) — resolving each ISIN to a Yahoo symbol via
     * the existing search endpoint. Returns a preview the UI can show for
     * confirmation before anything is written to the portfolio.
     */
    suspend fun previewBoursoCsv(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val bytes = runCatching { readBytes(uri) }.getOrElse {
            return@withContext ImportResult(emptyList(), 0, "Lecture du fichier impossible.", false)
        }
        val parsed = if (XlsxReader.looksLikeXlsx(bytes)) {
            BoursoCsvParser.parseRows(XlsxReader.readFirstSheet(bytes))
        } else {
            BoursoCsvParser.parse(decodeText(bytes))
        }
        if (parsed.trades.isEmpty()) {
            return@withContext ImportResult(
                emptyList(), parsed.skipped, parsed.error ?: "Aucune transaction lisible.", parsed.snapshot
            )
        }
        val resolved = HashMap<String, String?>()
        val rows = parsed.trades.map { d ->
            val symbol = if (d.isIsin) {
                resolved.getOrPut(d.rawSymbol) { api.searchSymbols(d.rawSymbol).firstOrNull()?.symbol }
            } else d.rawSymbol
            ImportRow(
                trade = Trade(
                    id = UUID.randomUUID().toString(),
                    symbol = (symbol ?: d.rawSymbol).uppercase(),
                    side = d.side,
                    quantity = d.quantity,
                    price = d.price,
                    ts = d.ts,
                    fees = d.fees,
                    note = d.note
                ),
                label = d.label,
                rawSymbol = d.rawSymbol,
                resolved = !d.isIsin || symbol != null
            )
        }
        ImportResult(rows, parsed.skipped, null, parsed.snapshot)
    }

    private fun readBytes(uri: Uri): ByteArray =
        getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)

    /** BoursoBank CSV exports are frequently Windows-1252; fall back if UTF-8 mangles accents. */
    private fun decodeText(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (utf8.contains('�')) String(bytes, charset("windows-1252")) else utf8
    }
    fun updateSettings(transform: (Settings) -> Settings) =
        viewModelScope.launch { repo.updateSettings(transform) }
    fun clearAlerts() = viewModelScope.launch { repo.clearAlerts() }

    fun startMonitoring(context: Context) = MonitorService.start(context)
    fun stopMonitoring(context: Context) = MonitorService.stop(context)

    /** One-off refresh for the UI (does not fire alerts). Also warms the sparkline cache. */
    fun refreshOnce() = viewModelScope.launch {
        watchlist.value.forEach { symbol ->
            launch { api.getQuote(symbol)?.let { repo.recordQuote(it) } }
            loadSparkline(symbol)
        }
    }

    /**
     * Lazily load the intraday series for one symbol's row sparkline, cached by
     * symbol so it never re-fetches on recomposition. Falls back to a two-point
     * previousClose → price series when Yahoo returns no history, so a row always
     * shows a direction cue.
     */
    fun loadSparkline(symbol: String) {
        if (_sparklines.value.containsKey(symbol)) return
        viewModelScope.launch {
            val history = api.getHistory(symbol, HistoryRange.DAYS)
            val series = if (history.size >= 2) {
                history.takeLast(40)
            } else {
                val q = quotes.value[symbol]
                val prev = q?.previousClose
                if (q != null && prev != null) {
                    listOf(PricePoint(q.ts - 1L, prev), PricePoint(q.ts, q.price))
                } else {
                    emptyList()
                }
            }
            if (series.isNotEmpty()) _sparklines.update { it + (symbol to series) }
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

    /** Optional Claude downside-risk radar over the tracked assets, fed the previous risk advice. */
    suspend fun riskAdvice(): Result<RiskAdvice> {
        val s = settings.value
        val prev = aiRisk.value
        return aiApi.risks(
            watchlist.value, quotes.value, s.anthropicApiKey, s.aiModel,
            previous = prev?.risk, previousTs = prev?.ts
        ).onSuccess { repo.saveRisk(it) }
    }

    fun sendTestWhatsApp(onResult: (Boolean) -> Unit) = viewModelScope.launch {
        onResult(WhatsAppSender.send(settings.value, "✅ Message test MarketTracker"))
    }
}

/** One reviewable line of a BoursoBank CSV import: the trade plus its display context. */
data class ImportRow(
    val trade: Trade,
    val label: String,
    val rawSymbol: String,
    val resolved: Boolean   // false = ISIN we couldn't map to a Yahoo symbol (no live quote)
)

/**
 * Result of previewing an import: reviewable [rows], count [skipped], optional
 * [error], and whether the source was a positions [snapshot] (vs a history).
 */
data class ImportResult(
    val rows: List<ImportRow>,
    val skipped: Int,
    val error: String?,
    val snapshot: Boolean
)
