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
        model: String,
        previous: TrendAnalysis? = null,
        previousTs: Long? = null
    ): Result<TrendAnalysis> = withContext(Dispatchers.IO) {
        val system = """
            Tu es un analyste de marché prudent et factuel qui explique simplement. On te fournit des
            indicateurs techniques calculés sur l'appareil pour un actif financier. Utilise l'outil de
            recherche web pour trouver les actualités récentes, le sentiment des analystes et les
            tendances du secteur concernant cet actif, puis synthétise une lecture équilibrée de la
            tendance. Signale les risques dans les deux sens. Tu ne donnes pas de conseil
            d'investissement personnalisé et tu ne recommandes jamais d'acheter ou de vendre.

            STYLE : écris pour une personne non spécialiste. Phrases courtes. Français simple.
            Évite le jargon ; si un terme technique est indispensable, explique-le en quelques mots.
            Chaque "driver" fait une ligne courte et concrète (≤ 15 mots).

            Réponds UNIQUEMENT avec un objet JSON valide (aucun texte avant ou après, pas de Markdown),
            avec exactement ces clés :
            {"direction":"hausse|baisse|neutre","confidence":0-100,"summary":"2 à 4 phrases claires en français",
             "drivers":["facteur clé court","..."],"horizon":"court terme|moyen terme|long terme + précision"}
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
            if (previous != null) {
                append("\nAnalyse précédente (${agoFr(previousTs)}) : direction=${previous.direction}, ")
                append("confiance=${previous.confidence}%. Résumé : ${previous.summary}\n")
                append("Actualise cette lecture avec les informations récentes et indique brièvement ")
                append("ce qui a changé depuis (dans le résumé ou un driver).\n")
            }
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
        model: String,
        previous: MarketBrief? = null,
        previousTs: Long? = null
    ): Result<MarketBrief> = withContext(Dispatchers.IO) {
        if (symbols.isEmpty()) {
            return@withContext Result.failure(Exception("Aucun actif à analyser. Ajoutez des symboles à votre liste."))
        }
        val system = """
            Tu es un analyste de marché prudent et factuel qui explique simplement. On te fournit la
            liste de suivi d'un utilisateur avec les cours actuels. Utilise l'outil de recherche web
            pour connaître le contexte de marché du jour et, surtout, pour repérer les MOUVEMENTS
            MAJEURS de la journée (fortes hausses et fortes baisses). Inclus à la fois les actifs de
            la liste de suivi ET des actifs notables qui n'y sont PAS, tant que leur mouvement est
            important. Puis rédige un brief court et équilibré. Tu ne donnes aucun conseil
            d'investissement personnalisé.

            STYLE : écris pour une personne non spécialiste. Phrases courtes. Français simple.
            Évite le jargon ; explique brièvement tout terme technique. Chaque "highlight" et chaque
            "note" fait une ligne courte (≤ 15 mots).

            Réponds UNIQUEMENT avec un objet JSON valide (aucun texte avant ou après, pas de Markdown),
            avec exactement ces clés :
            {"sentiment":"haussier|baissier|mitigé",
             "summary":"3 à 5 phrases claires en français",
             "highlights":["point marquant court","..."],
             "movers":[{"symbol":"TICKER","name":"Nom","changePct":8.2,"note":"raison courte"}]}
            Dans "movers", changePct est la variation du jour en % (nombre, positif ou négatif ;
            null si inconnu). Classe du plus fort mouvement au plus faible. Vise 4 à 8 mouvements.
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
            if (previous != null) {
                append("\nBrief précédent (${agoFr(previousTs)}) : sentiment=${previous.sentiment}. ")
                append("${previous.summary}\n")
                append("Tiens-en compte et souligne ce qui a évolué depuis.\n")
            }
            append("\nRecherche le contexte de marché actuel. Dans \"movers\", inclus les gros ")
            append("mouvements du jour de cette liste ET des actifs importants hors liste. ")
            append("Rends l'objet JSON demandé.")
        }

        request(system, user, apiKey, model, maxTokens = 2500).mapCatching { raw ->
            val json = extractJson(raw.text)
                ?: return@mapCatching MarketBrief(
                    sentiment = "mitigé",
                    summary = raw.text.ifBlank { "Brief indisponible." },
                    highlights = emptyList(),
                    movers = emptyList(),
                    sources = raw.sources
                )
            MarketBrief(
                sentiment = json.optString("sentiment", "mitigé").ifBlank { "mitigé" },
                summary = json.optString("summary").ifBlank { "Brief indisponible." },
                highlights = stringList(json.optJSONArray("highlights")),
                movers = parseMovers(json.optJSONArray("movers")),
                sources = raw.sources
            )
        }
    }

    /**
     * Forward-looking opportunities: for each horizon (1 semaine → 1 an), assets the user does
     * **not** already track that carry a very high potential increase, per market analytics + news.
     * The watchlist [symbols] are passed only as an exclusion list — nothing from it is proposed,
     * and any that slips through is filtered out below. Speculative, informational only.
     */
    suspend fun forecast(
        symbols: List<String>,
        quotes: Map<String, Quote>,
        apiKey: String,
        model: String,
        previous: ForecastAdvice? = null,
        previousTs: Long? = null
    ): Result<ForecastAdvice> = withContext(Dispatchers.IO) {
        val horizons = ForecastAdvice.HORIZONS.joinToString(", ")
        val system = """
            Tu es un analyste de marché prudent qui explique simplement. Ta mission : DÉNICHER des
            opportunités NOUVELLES — des actifs que l'utilisateur ne suit PAS encore et qui
            présentent un TRÈS FORT potentiel de HAUSSE. Utilise l'outil de recherche web pour
            balayer tout le marché (actions, ETF, cryptos, indices, matières premières…) et trouver
            des analyses et actualités récentes.

            RÈGLE ABSOLUE : ne propose JAMAIS un actif figurant dans la liste de suivi de
            l'utilisateur (fournie ci-dessous). Ces actifs sont déjà suivis, donc EXCLUS. Cherche
            ailleurs, y compris des valeurs moins connues à fort potentiel.

            Pour CHAQUE horizon, ne retiens que des actifs HORS liste au plus fort potentiel de
            hausse. Privilégie un potentiel élevé (vise ≥ 10 % à court terme, davantage sur les
            horizons longs) tout en restant crédible et factuel. Ce sont des scénarios spéculatifs,
            PAS un conseil en investissement : mentionne le risque, ne dis jamais d'acheter ou de vendre.

            STYLE : français simple, phrases courtes, sans jargon. Chaque "rationale" ≤ 20 mots.

            Horizons demandés (utilise exactement ces libellés) : $horizons.
            Réponds UNIQUEMENT avec un objet JSON valide (aucun texte avant ou après, pas de Markdown) :
            {"horizons":[
              {"label":"1 semaine","opportunities":[
                {"symbol":"TICKER","name":"Nom","potentialPct":12.0,"rationale":"raison courte"}]},
              {"label":"1 mois","opportunities":[...]},
              {"label":"6 mois","opportunities":[...]},
              {"label":"1 an","opportunities":[...]}]}
            "potentialPct" est le potentiel de hausse estimé en % (nombre spéculatif ; null si inconnu).
            Vise 2 à 4 idées par horizon, classées du plus fort potentiel au plus faible. Utilise des
            tickers Yahoo Finance valides (ex. AAPL, NVDA, MC.PA, BTC-USD).
        """.trimIndent()

        val user = buildString {
            if (symbols.isNotEmpty()) {
                append("Liste de suivi de l'utilisateur — actifs à EXCLURE de tes propositions ")
                append("(${symbols.size}) :\n")
                symbols.forEach { s ->
                    val q = quotes[s]
                    append("- $s (${marketOf(s).label})")
                    if (q != null) {
                        append(" : ${fmt(q.price)}")
                        q.dayChangePct?.let { append(" (${signedPct(it)})") }
                    }
                    append("\n")
                }
                append("N'inclus AUCUN de ces symboles dans ta réponse.\n")
            } else {
                append("L'utilisateur ne suit encore aucun actif : propose librement, mais ")
                append("uniquement des idées à très fort potentiel de hausse.\n")
            }
            if (previous != null && previous.horizons.isNotEmpty()) {
                append("\nPrévision précédente (${agoFr(previousTs)}) :\n")
                previous.horizons.forEach { h ->
                    val names = h.opportunities.joinToString(", ") { it.symbol }
                    if (names.isNotBlank()) append("- ${h.label} : $names\n")
                }
                append("Réévalue ces idées avec les informations récentes : conserve celles qui ")
                append("tiennent (et restent hors liste de suivi), retire celles qui ne sont plus ")
                append("pertinentes, ajoute de nouvelles pépites.\n")
            }
            append("\nRecherche à travers tout le marché des actifs NON suivis à très fort potentiel ")
            append("de hausse, puis rends l'objet JSON demandé (2 à 4 idées par horizon).")
        }

        request(system, user, apiKey, model, maxTokens = 3000).mapCatching { raw ->
            val json = extractJson(raw.text)
                ?: return@mapCatching ForecastAdvice(emptyList(), raw.sources)
            // Safety net: never surface something already tracked, even if the model ignores the rule.
            val excluded = symbols.mapTo(HashSet()) { it.trim().uppercase() }
            val horizonsOut = parseHorizons(json.optJSONArray("horizons")).map { h ->
                h.copy(opportunities = h.opportunities.filterNot { it.symbol in excluded })
            }
            ForecastAdvice(horizons = horizonsOut, sources = raw.sources)
        }
    }

    /**
     * Downside-risk radar: among the user's **tracked** assets, the ones with the highest risk of
     * a near-term decline, per market analytics + news. The watchlist [symbols] are the candidate
     * pool — only tracked symbols are surfaced (anything off-list is filtered out below). Speculative.
     */
    suspend fun risks(
        symbols: List<String>,
        quotes: Map<String, Quote>,
        apiKey: String,
        model: String,
        previous: RiskAdvice? = null,
        previousTs: Long? = null
    ): Result<RiskAdvice> = withContext(Dispatchers.IO) {
        if (symbols.isEmpty()) {
            return@withContext Result.failure(
                Exception("Aucun actif suivi à analyser. Ajoutez des symboles à votre liste.")
            )
        }
        val system = """
            Tu es un analyste de marché prudent qui explique simplement. Ta mission : parmi les
            actifs SUIVIS par l'utilisateur (liste fournie ci-dessous), repérer ceux qui présentent
            un RISQUE ÉLEVÉ de BAISSE à court/moyen terme. Utilise l'outil de recherche web pour
            trouver les analyses et actualités récentes (résultats décevants, dégradations d'analystes,
            valorisation tendue, risques sectoriels ou réglementaires, momentum négatif…).

            RÈGLE ABSOLUE : n'évalue QUE les actifs de la liste de suivi ci-dessous. Ne propose aucun
            actif hors de cette liste. Ne retiens que ceux au risque réellement notable ; s'il n'y a
            pas de risque marquant, renvoie une liste vide.

            Ce sont des scénarios spéculatifs, PAS un conseil en investissement : reste factuel,
            explique le risque, ne dis jamais d'acheter ou de vendre.

            STYLE : français simple, phrases courtes, sans jargon. Chaque "rationale" ≤ 20 mots.

            Réponds UNIQUEMENT avec un objet JSON valide (aucun texte avant ou après, pas de Markdown) :
            {"risks":[
              {"symbol":"TICKER","name":"Nom","downsidePct":-15.0,"severity":"élevé",
               "rationale":"raison courte"}]}
            "downsidePct" est la baisse potentielle estimée en % (nombre NÉGATIF spéculatif ; null si
            inconnu). "severity" vaut "élevé", "modéré" ou "faible". Classe du risque le plus fort au
            plus faible. Vise 2 à 5 actifs à risque (moins s'il y en a peu).
        """.trimIndent()

        val user = buildString {
            append("Liste de suivi de l'utilisateur — actifs à évaluer (${symbols.size}) :\n")
            symbols.forEach { s ->
                val q = quotes[s]
                append("- $s (${marketOf(s).label})")
                if (q != null) {
                    append(" : ${fmt(q.price)}")
                    q.dayChangePct?.let { append(" (${signedPct(it)})") }
                }
                append("\n")
            }
            if (previous != null && previous.warnings.isNotEmpty()) {
                append("\nAlerte de risque précédente (${agoFr(previousTs)}) : ")
                append(previous.warnings.joinToString(", ") { it.symbol })
                append("\nRéévalue ces risques avec les informations récentes : conserve ceux qui ")
                append("tiennent, retire ceux qui se sont dissipés, ajoute les nouveaux.\n")
            }
            append("\nN'évalue QUE les actifs ci-dessus. Recherche les actualités récentes, puis ")
            append("rends l'objet JSON demandé avec les actifs suivis les plus à risque de baisse.")
        }

        request(system, user, apiKey, model, maxTokens = 2500).mapCatching { raw ->
            val json = extractJson(raw.text)
                ?: return@mapCatching RiskAdvice(emptyList(), raw.sources)
            // Safety net: only ever surface tracked assets, even if the model wanders off-list.
            val tracked = symbols.mapTo(HashSet()) { it.trim().uppercase() }
            val warnings = parseRisks(json.optJSONArray("risks")).filter { it.symbol in tracked }
            RiskAdvice(warnings = warnings, sources = raw.sources)
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

    private fun parseMovers(arr: JSONArray?): List<Mover> {
        arr ?: return emptyList()
        val out = ArrayList<Mover>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val symbol = o.optString("symbol").trim().uppercase()
            if (symbol.isEmpty()) continue
            out.add(
                Mover(
                    symbol = symbol,
                    name = o.optString("name").trim(),
                    changePct = if (o.has("changePct") && !o.isNull("changePct")) o.optDouble("changePct") else null,
                    note = o.optString("note").trim()
                )
            )
        }
        return out
    }

    private fun parseHorizons(arr: JSONArray?): List<HorizonForecast> {
        arr ?: return emptyList()
        val out = ArrayList<HorizonForecast>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val label = o.optString("label").trim()
            if (label.isEmpty()) continue
            out.add(HorizonForecast(label, parseOpportunities(o.optJSONArray("opportunities"))))
        }
        return out
    }

    private fun parseOpportunities(arr: JSONArray?): List<Opportunity> {
        arr ?: return emptyList()
        val out = ArrayList<Opportunity>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val symbol = o.optString("symbol").trim().uppercase()
            if (symbol.isEmpty()) continue
            out.add(
                Opportunity(
                    symbol = symbol,
                    name = o.optString("name").trim(),
                    potentialPct = if (o.has("potentialPct") && !o.isNull("potentialPct")) o.optDouble("potentialPct") else null,
                    rationale = o.optString("rationale").trim()
                )
            )
        }
        return out
    }

    private fun parseRisks(arr: JSONArray?): List<RiskWarning> {
        arr ?: return emptyList()
        val out = ArrayList<RiskWarning>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val symbol = o.optString("symbol").trim().uppercase()
            if (symbol.isEmpty()) continue
            out.add(
                RiskWarning(
                    symbol = symbol,
                    name = o.optString("name").trim(),
                    downsidePct = if (o.has("downsidePct") && !o.isNull("downsidePct")) o.optDouble("downsidePct") else null,
                    severity = o.optString("severity").trim().ifBlank { "élevé" },
                    rationale = o.optString("rationale").trim()
                )
            )
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

    /** Rough French age of a cached result, for the "previous result" prompt context. */
    private fun agoFr(ts: Long?): String {
        ts ?: return "précédemment"
        val days = (System.currentTimeMillis() - ts) / 86_400_000L
        return when {
            days <= 0L -> "aujourd'hui"
            days == 1L -> "hier"
            else -> "il y a $days jours"
        }
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
