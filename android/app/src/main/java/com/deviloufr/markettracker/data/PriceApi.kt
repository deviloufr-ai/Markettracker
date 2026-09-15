package com.deviloufr.markettracker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Price source: the (unofficial) Yahoo Finance chart endpoint. No API key, and
 * it returns a per-minute volume bar, which is what the volume rule needs.
 */
class PriceApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    suspend fun getQuote(symbol: String): Quote? = withContext(Dispatchers.IO) {
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/$symbol?interval=1m&range=1d"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MarketTracker/1.0 (Android)")
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                parse(symbol, body)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Historical closes for [symbol] over the given [range], oldest first.
     * Returns an empty list on any network/parse error or when Yahoo has no
     * data for the window (markets closed, delisted symbol, …).
     */
    suspend fun getHistory(symbol: String, range: HistoryRange): List<PricePoint> =
        withContext(Dispatchers.IO) {
            val url = "https://query1.finance.yahoo.com/v8/finance/chart/$symbol" +
                "?interval=${range.interval}&${range.rangeQuery()}"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MarketTracker/1.0 (Android)")
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    val body = resp.body?.string() ?: return@withContext emptyList()
                    parseHistory(body)
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    /**
     * Live symbol search against Yahoo's search endpoint, for assets outside the
     * curated [AssetCatalog]. Returns an empty list on any network/parse error.
     */
    suspend fun searchSymbols(query: String): List<Asset> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val enc = java.net.URLEncoder.encode(q, "UTF-8")
        val url = "https://query1.finance.yahoo.com/v1/finance/search" +
            "?q=$enc&quotesCount=20&newsCount=0&listsCount=0"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MarketTracker/1.0 (Android)")
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string() ?: return@withContext emptyList()
                parseSearch(body)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseSearch(body: String): List<Asset> {
        return try {
            val quotes = JSONObject(body).optJSONArray("quotes") ?: return emptyList()
            val out = ArrayList<Asset>(quotes.length())
            for (i in 0 until quotes.length()) {
                val o = quotes.optJSONObject(i) ?: continue
                val symbol = o.optString("symbol").trim()
                if (symbol.isEmpty()) continue
                val name = listOf("shortname", "longname", "shortName", "longName")
                    .firstNotNullOfOrNull { key -> o.optString(key).takeIf { it.isNotBlank() } }
                    ?: symbol
                out.add(Asset(symbol = symbol, name = name))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseHistory(body: String): List<PricePoint> {
        return try {
            val result = JSONObject(body)
                .getJSONObject("chart")
                .getJSONArray("result")
                .getJSONObject(0)
            val ts = result.optJSONArray("timestamp") ?: return emptyList()
            val closes = result.optJSONObject("indicators")
                ?.optJSONArray("quote")?.optJSONObject(0)
                ?.optJSONArray("close") ?: return emptyList()

            val out = ArrayList<PricePoint>(ts.length())
            val n = minOf(ts.length(), closes.length())
            for (i in 0 until n) {
                if (closes.isNull(i)) continue // Yahoo leaves gaps as null
                out.add(PricePoint(ts = ts.getLong(i) * 1000L, close = closes.getDouble(i)))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parse(symbol: String, body: String): Quote? {
        return try {
            val result = JSONObject(body)
                .getJSONObject("chart")
                .getJSONArray("result")
                .getJSONObject(0)
            val meta = result.getJSONObject("meta")

            val price = meta.optDouble("regularMarketPrice", Double.NaN)
            if (price.isNaN()) return null

            val prevClose = when {
                meta.has("chartPreviousClose") -> meta.optDouble("chartPreviousClose", Double.NaN)
                meta.has("previousClose") -> meta.optDouble("previousClose", Double.NaN)
                else -> Double.NaN
            }
            val ts = meta.optLong("regularMarketTime", System.currentTimeMillis() / 1000) * 1000

            var volume: Double? = null
            val quoteArr = result.optJSONObject("indicators")?.optJSONArray("quote")
            if (quoteArr != null && quoteArr.length() > 0) {
                val volArr = quoteArr.getJSONObject(0).optJSONArray("volume")
                if (volArr != null) {
                    var i = volArr.length() - 1
                    while (i >= 0) {
                        if (!volArr.isNull(i)) { volume = volArr.getDouble(i); break }
                        i--
                    }
                }
            }

            Quote(symbol, price, volume, if (prevClose.isNaN()) null else prevClose, ts)
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Time windows offered on the history chart, mapped to Yahoo chart parameters.
 * Yahoo has no <5d preset, so [WEEK] uses an explicit period1/period2 window.
 * Longer windows step the candle interval up (daily, then weekly) to keep the
 * point count reasonable.
 */
enum class HistoryRange(val label: String, val interval: String) {
    HOURS("Heures", "5m"),
    DAYS("Jours", "30m"),
    WEEK("Semaine", "60m"),
    MONTH("Mois", "1d"),
    MONTH_3("3 mois", "1d"),
    MONTH_6("6 mois", "1d"),
    YEAR("1 an", "1d"),
    YEAR_5("5 ans", "1wk");

    /** The `range=…` or `period1/period2=…` fragment of the Yahoo chart query. */
    fun rangeQuery(nowMillis: Long = System.currentTimeMillis()): String = when (this) {
        HOURS -> "range=1d"
        DAYS -> "range=5d"
        WEEK -> {
            val nowSec = nowMillis / 1000L
            "period1=${nowSec - 7L * 24 * 3600}&period2=$nowSec"
        }
        MONTH -> "range=1mo"
        MONTH_3 -> "range=3mo"
        MONTH_6 -> "range=6mo"
        YEAR -> "range=1y"
        YEAR_5 -> "range=5y"
    }
}
