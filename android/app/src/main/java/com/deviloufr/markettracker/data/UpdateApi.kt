package com.deviloufr.markettracker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** One published app build, as described by the releases-repo update manifest. */
data class AppRelease(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
)

/**
 * Reads the update manifest CI publishes to the repo's **public** GitHub Releases.
 * The `releases/latest/download/<asset>` URLs are stable redirects that always
 * resolve to the newest release, so the app needs neither the GitHub API nor a
 * token — a plain HTTPS GET is enough (works once the repo is public).
 */
class UpdateApi(
    private val manifestUrl: String = DEFAULT_MANIFEST_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    /** Latest published build, or null on any network/parse error (incl. no releases yet). */
    suspend fun fetchLatest(): AppRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(manifestUrl)
            .header("User-Agent", "MarketTracker/1.0 (Android)")
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                parse(body)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parse(body: String): AppRelease? = try {
        val o = JSONObject(body)
        val code = o.optInt("versionCode", -1)
        val apk = o.optString("apkUrl").trim()
        if (code < 0 || apk.isEmpty()) {
            null
        } else {
            AppRelease(
                versionCode = code,
                versionName = o.optString("versionName").ifBlank { "?" },
                notes = o.optString("notes").trim(),
                apkUrl = apk,
            )
        }
    } catch (e: Exception) {
        null
    }

    companion object {
        /** Owner/repo whose public GitHub Releases carry the builds. */
        const val RELEASES_REPO = "deviloufr-ai/Markettracker"

        /** Stable "latest" manifest URL — resolves to the newest release's latest.json. */
        const val DEFAULT_MANIFEST_URL =
            "https://github.com/$RELEASES_REPO/releases/latest/download/latest.json"
    }
}
