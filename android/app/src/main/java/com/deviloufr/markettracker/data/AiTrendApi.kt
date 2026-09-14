package com.deviloufr.markettracker.data

import com.deviloufr.markettracker.ui.marketOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Optional "Analyse IA" client. Calls the Claude Messages API directly over OkHttp (the same
 * networking stack [PriceApi] uses) with the server-side **web search** tool enabled, so Claude
 * reads current news / analyst sentiment from financial sites and combines it with the on-device
 * [TechnicalSignals] we pass in.
 *
 * The API key is the user's own, entered in Settings and stored on-device — this is a personal-use
 * pattern (calls bill the user's Anthropic account); a public app would proxy this through a
 * backend. All results are informational, not financial advice.
 */
class AiTrendApi(
    // Web search + reasoning can take a while, so this client is far more patient than [PriceApi].
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()
) {
    /** Per-ticker deep analysis. [Result.failure] carries a French, user-facing message. */
    suspend fun analyze(
        symbol: String,
        quote: Quote?,
        signals: TechnicalSignals,
        apiKey: String,
        model: String
    ): Result<TrendAnalysis> = withContext(Dispatchers.IO) {
        val system = """
            Tu es un analyste de marché prudent et factuel. On te fournit des indicateurs techniques
            calculés sur l'appareil pour un actif financier. Utilise l'outil de recherche web pour
            trouver les actualités récentes, le sentiment des analystes et les tendances du secteur
            concernant cet actif, puis synthétise une lecture équilibrée de la tendance.
            Signale les risques dans les deux sens. Tu ne donnes pas de conseil d'investissement
            personnalisé et tu ne recommandes jamais d'acheter ou de vendre.
            Réponds UNIQUEMENT avec un objet JSON valide (aucun texte avant ou après, pas de Markdown),
            avec exactement ces clés :
            {"direction":"hausse|baisse|neutre","confidence":0-100,"summary":"2 à 4 phrases en français",
             "drivers":["facteur clé","..."],"horizon":"court terme|moyen terme|long terme + précision"}
        """.trimIndent()

        val user = buildString {
            append("Actif : $symbol (${marketOf(symbol).label}).\n")
            if (quote != null) {
                append("Cours actuel : ${fmt(quote.price)}")
                quote.dayChangePct?.let { append(" (${signedPct(it)} aujourd'hui)") }
                quote.volume?.let { append(", volume récent ${fmt(it)}") }
                append(".\n")
            }
            append("\nIndicateurs techniques (sur l'appareil) :\n")
            append("- Verdict local : ${verdictLabelFr(signals.verdict)} (confiance ${signals.confidence}%).\n")
            append("- Momentum sur la période : ${signedPct(signals.momentumPct)}.\n")
            signals.rsi?.let { append("- RSI(14) : ${fmt1(it)}.\n") }
            append("- ${signals.trendLabel}.\n")
            append("- Volatilité : ${fmt1(signals.volatilityPct)} %.\n")
            append("- Position dans la fourchette de la période : ${fmt1(signals.rangePositionPct)} % (0 = plus bas, 100 = plus haut).\n")
            append("\nRecherche les actualités récentes de $symbol et rends l'objet JSON demandé.")
        }

        request(system, user, apiKey, model, maxTokens = 2000).mapCatching { raw ->
            val json = extractJson(raw.text)
                ?: return@mapCatching TrendAnalysis(
                    direction = "neutre",
                    confidence = signals.confidence,
                    summary = raw.text.ifBlank { "Analyse indisponible." },
                    drivers = emptyList(),
                    horizon = "court terme",
                    sources = raw.sources
                )
            TrendAnalysis(
                direction = json.optString("direction", "neutre").ifBlank { "neutre" },
                confidence = json.optInt("confidence", signals.confidence).coerceIn(0, 100),
                summary = json.optString("summary").ifBlank { "Analyse indisponible." },
                drivers = stringList(json.optJSONArray("drivers")),
                horizon = json.optString("horizon", "court terme"),
                sources = raw.sources
            )
        }
    }

    /** Watchlist-wide market brief across [symbols], given the current [quotes] we already hold. */
    suspend fun brief(
        symbols: List<String>,
        quotes: Map<String, Quote>,
        apiKey: String,
        model: String
    ): Result<MarketBrief> = withContext(Dispatchers.IO) {
        if (symbols.isEmpty()) {
            return@withContext Result.failure(Exception("Aucun actif à analyser. Ajoutez des symboles à votre liste."))
        }
        val system = """
            Tu es un analyste de marché prudent et factuel. On te fournit la liste de suivi d'un
            utilisateur avec les cours actuels. Utilise l'outil de recherche web pour connaître le
            contexte de marché du jour (actualités macro, mouvements notables des actifs de la liste),
            puis rédige un brief court et équilibré. Tu ne donnes aucun conseil d'investissement
            personnalisé.
            Réponds UNIQUEMENT avec un objet JSON valide (aucun texte avant ou après, pas de Markdown),
            avec exactement ces clés :
            {"sentiment":"haussier|baissier|mitigé","summary":"3 à 5 phrases en français",
             "highlights":["SYMBOLE : point marquant","..."]}
        """.trimIndent()

        val user = buildString {
            append("Liste de suivi (${symbols.size} actifs) :\n")
            symbols.forEach { s ->
                val q = quotes[s]
                append("- $s (${marketOf(s).label})")
                if (q != null) {
                    append(" : ${fmt(q.price)}")
                    q.dayChangePct?.let { append(" (${signedPct(it)})") }
                }
                append("\n")
            }
            append("\nRecherche le contexte de marché actuel et rends l'objet JSON demandé.")
        }

        request(system, user, apiKey, model, maxTokens = 2500).mapCatching { raw ->
            val json = extractJson(raw.text)
                ?: return@mapCatching MarketBrief(
                    sentiment = "mitigé",
                    summary = raw.text.ifBlank { "Brief indisponible." },
                    highlights = emptyList(),
                    sources = raw.sources
                )
            MarketBrief(
                sentiment = json.optString("sentiment", "mitigé").ifBlank { "mitigé" },
                summary = json.optString("summary").ifBlank { "Brief indisponible." },
                highlights = stringList(json.optJSONArray("highlights")),
                sources = raw.sources
            )
        }
    }

    // --- shared request/response plumbing ------------------------------------------------------

    private data class Raw(val text: String, val sources: List<AiSource>)

    private fun request(
        system: String,
        userContent: String,
        apiKey: String,
        model: String,
        maxTokens: Int
    ): Result<Raw> {
        val key = apiKey.trim()
        if (key.isEmpty()) return Result.failure(Exception("Clé API Anthropic manquante (à renseigner dans Réglages)."))

        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", maxTokens)
            put("thinking", JSONObject().put("type", "adaptive"))
            put("output_config", JSONObject().put("effort", "medium"))
            put("tools", JSONArray().put(
                JSONObject()
                    .put("type", "web_search_20260209")
                    .put("name", "web_search")
                    .put("max_uses", 5)
            ))
            put("system", system)
            put("messages", JSONArray().put(
                JSONObject().put("role", "user").put("content", userContent)
            ))
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", key)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        return try {
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Result.failure(Exception(httpErrorMessage(resp.code, text)))
                } else {
                    parse(text)
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Échec de la connexion à l'API. Vérifiez votre réseau."))
        }
    }

    private fun parse(body: String): Result<Raw> {
        return try {
            val root = JSONObject(body)
            if (root.optString("stop_reason") == "refusal") {
                return Result.failure(Exception("Le modèle a refusé de répondre à cette demande."))
            }
            val content = root.optJSONArray("content")
                ?: return Result.failure(Exception("Réponse inattendue de l'API."))

            val text = StringBuilder()
            val sources = LinkedHashMap<String, AiSource>() // dedupe by URL, keep order

            for (i in 0 until content.length()) {
                val block = content.optJSONObject(i) ?: continue
                when (block.optString("type")) {
                    "text" -> {
                        text.append(block.optString("text"))
                        collectCitations(block.optJSONArray("citations"), sources)
                    }
                    "web_search_tool_result" -> {
                        // On error, "content" is an object, not an array — optJSONArray guards that.
                        val results = block.optJSONArray("content") ?: continue
                        for (j in 0 until results.length()) {
                            val r = results.optJSONObject(j) ?: continue
                            if (r.optString("type") == "web_search_result") addSource(r, sources)
                        }
                    }
                }
            }
            Result.success(Raw(text.toString().trim(), sources.values.take(6).toList()))
        } catch (e: JSONException) {
            Result.failure(Exception("Impossible de lire la réponse de l'API."))
        }
    }

    private fun collectCitations(citations: JSONArray?, into: LinkedHashMap<String, AiSource>) {
        citations ?: return
        for (i in 0 until citations.length()) {
            val c = citations.optJSONObject(i) ?: continue
            addSource(c, into)
        }
    }

    private fun addSource(obj: JSONObject, into: LinkedHashMap<String, AiSource>) {
        val url = obj.optString("url")
        if (url.isBlank()) return
        val title = obj.optString("title").ifBlank { url }
        into.getOrPut(url) { AiSource(title, url) }
    }

    /** Extract the first balanced-ish JSON object from [text], tolerating stray prose/Markdown. */
    private fun extractJson(text: String): JSONObject? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JSONObject(text.substring(start, end + 1))
        } catch (e: JSONException) {
            null
        }
    }

    private fun stringList(arr: JSONArray?): List<String> {
        arr ?: return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.optString(i).trim()
            if (s.isNotEmpty()) out.add(s)
        }
        return out
    }

    private fun httpErrorMessage(code: Int, body: String): String {
        val apiMsg = try {
            JSONObject(body).optJSONObject("error")?.optString("message")
        } catch (e: JSONException) {
            null
        }
        return when (code) {
            401 -> "Clé API invalide. Vérifiez votre clé Anthropic dans Réglages."
            403 -> "Accès refusé par l'API (clé ou permissions)."
            429 -> "Trop de requêtes ou quota atteint. Réessayez plus tard."
            in 500..599 -> "Le service Anthropic est momentanément indisponible. Réessayez."
            else -> apiMsg?.let { "Erreur API : $it" } ?: "Erreur API (code $code)."
        }
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%,.2f", v)
    private fun fmt1(v: Double): String = String.format(Locale.US, "%.1f", v)
    private fun signedPct(v: Double): String = String.format(Locale.US, "%+.2f %%", v)

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
