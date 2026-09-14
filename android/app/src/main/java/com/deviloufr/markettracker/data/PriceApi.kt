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
