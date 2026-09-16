package com.deviloufr.markettracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deviloufr.markettracker.data.ForecastAdvice
import com.deviloufr.markettracker.data.MarketBrief
import com.deviloufr.markettracker.data.RiskAdvice
import com.deviloufr.markettracker.ui.theme.BrandIndigo
import com.deviloufr.markettracker.ui.theme.IndexTint

/**
 * Dedicated "Analyse IA" tab: the market brief and forward-looking opportunities,
 * both rendered by Claude with web search. Kept separate from the "Suivi"
 * (watchlist) tab so tracking stays uncluttered and the AI features have room.
 */
@Composable
fun AiScreen(
    vm: MarketViewModel,
    onAddAssets: () -> Unit = {}
) {
    val watchlist by vm.watchlist.collectAsState()
    val settings by vm.settings.collectAsState()

    // Brief run-state lives in the ViewModel, so switching tabs mid-analysis neither cancels
    // the request nor drops the loading/result state.
    val brief by vm.briefState.collectAsState()
    val cachedBrief = vm.aiBrief.collectAsState().value

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AiHeroCard(
                aiEnabled = settings.anthropicApiKey.isNotBlank(),
                briefLoading = brief is AiUiState.Loading,
                hasBrief = brief is AiUiState.Success,
                onGenerate = { vm.runBrief() }
            )
        }
        if (watchlist.isNotEmpty()) {
            item {
                MarketBriefSection(
                    briefState = brief,
                    keyMissing = settings.anthropicApiKey.isBlank(),
                    tracked = watchlist.toSet(),
                    generatedAt = cachedBrief?.ts,
                    onAdd = { vm.addTicker(it) },
                    onRetry = { vm.runBrief() }
                )
            }
        } else {
            item {
                Card {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Brief du marché IA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Ajoutez des actifs à votre liste de suivi pour un brief personnalisé du marché.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilledTonalButton(onClick = onAddAssets, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text("  Ajouter des actifs")
                        }
                    }
                }
            }
        }
        item { OpportunitiesCard(vm = vm, watchlist = watchlist) }
        if (watchlist.isNotEmpty()) {
            item { RisksCard(vm = vm) }
        }
    }
}

/** Brand-gradient hero for the AI tab, carrying the brief-generation trigger. */
@Composable
private fun AiHeroCard(
    aiEnabled: Boolean,
    briefLoading: Boolean,
    hasBrief: Boolean,
    onGenerate: () -> Unit
) {
    GradientHeroCard(colors = listOf(BrandIndigo, IndexTint)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(
                "Analyse IA",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Brief du marché & opportunités",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Rédigés par Claude avec recherche web : contexte du jour, mouvements majeurs "
                + "et potentiel par horizon. À titre informatif, pas un conseil financier.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.9f)
        )
        if (aiEnabled) {
            Spacer(Modifier.height(14.dp))
            HeroBriefButton(
                loading = briefLoading,
                hasBrief = hasBrief,
                modifier = Modifier.fillMaxWidth(),
                onClick = onGenerate
            )
        }
    }
}

/** Full-width brief trigger shown inside [AiHeroCard]. */
@Composable
private fun HeroBriefButton(loading: Boolean, hasBrief: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                Text("Analyse…", style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
            } else {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(
                    if (hasBrief) "Actualiser le brief" else "Générer le brief IA",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }
    }
}

/** AI brief results. The trigger button lives in the hero above. */
@Composable
private fun MarketBriefSection(
    briefState: AiUiState<MarketBrief>,
    keyMissing: Boolean,
    tracked: Set<String>,
    generatedAt: Long?,
    onAdd: (String) -> Unit,
    onRetry: () -> Unit
) {
    if (keyMissing) {
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Brief du marché IA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Ajoutez votre clé API Anthropic dans Réglages pour un brief rédigé par l'IA, "
                        + "avec le contexte de marché du jour issu du web.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }
    when (briefState) {
        AiUiState.Idle -> Unit // Nothing generated yet — the hero button prompts.
        AiUiState.Loading -> Card {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Analyse du marché sur le web…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        is AiUiState.Error -> Card {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                AiErrorRow(briefState.message, onRetry)
            }
        }
        is AiUiState.Success -> Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Brief du marché IA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                MarketBriefContent(
                    b = briefState.data,
                    tracked = tracked,
                    generatedAt = generatedAt,
                    onAdd = onAdd,
                    onRefresh = onRetry
                )
                AiDisclaimer()
            }
        }
    }
}

@Composable
private fun MarketBriefContent(
    b: MarketBrief,
    tracked: Set<String>,
    generatedAt: Long?,
    onAdd: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DirectionPill(b.sentiment, b.sentiment.replaceFirstChar { it.uppercase() })
        if (b.summary.isNotBlank()) {
            Text(b.summary, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
        }
        if (b.highlights.isNotEmpty()) {
            SectionLabel("À retenir")
            BulletList(b.highlights)
        }
        if (b.movers.isNotEmpty()) {
            SectionLabel("Mouvements majeurs")
            b.movers.forEach { mover ->
                AiAssetRow(
                    symbol = mover.symbol,
                    name = mover.name,
                    pct = mover.changePct,
                    note = mover.note,
                    tracked = mover.symbol in tracked,
                    onAdd = { onAdd(mover.symbol) }
                )
            }
        }
        AiSources(b.sources)
        if (generatedAt != null) {
            Text(
                "Généré ${timeAgo(generatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onRefresh, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("Actualiser le brief")
        }
    }
}

@Composable
private fun OpportunitiesCard(vm: MarketViewModel, watchlist: List<String>) {
    val settings by vm.settings.collectAsState()

    // Run-state lives in the ViewModel so it survives tab switches, navigation and restart.
    val state by vm.forecastState.collectAsState()
    val cached = vm.aiForecast.collectAsState().value

    fun run() = vm.runForecast()

    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Opportunités IA — à découvrir", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Des actifs que vous ne suivez pas encore, au très fort potentiel de hausse, par "
                    + "horizon (1 semaine → 1 an), d'après les analyses et actualités du marché. "
                    + "Scénarios spéculatifs, pas un conseil en investissement.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (settings.anthropicApiKey.isBlank()) {
                Text(
                    "Ajoutez votre clé API Anthropic dans Réglages pour générer les opportunités.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                when (val s = state) {
                    AiUiState.Idle -> OutlinedButton(onClick = { run() }, modifier = Modifier.fillMaxWidth()) {
                        Text("🔮  Découvrir des opportunités")
                    }
                    AiUiState.Loading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Analyse des perspectives sur le web…", style = MaterialTheme.typography.bodyMedium)
                    }
                    is AiUiState.Error -> AiErrorRow(s.message) { run() }
                    is AiUiState.Success -> ForecastContent(
                        f = s.data,
                        tracked = watchlist.toSet(),
                        generatedAt = cached?.ts,
                        onAdd = { vm.addTicker(it) },
                        onRefresh = { run() }
                    )
                }
                AiDisclaimer()
            }
        }
    }
}

@Composable
private fun ForecastContent(
    f: ForecastAdvice,
    tracked: Set<String>,
    generatedAt: Long?,
    onAdd: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (f.horizons.isEmpty()) {
            Text("Aucune opportunité proposée.", style = MaterialTheme.typography.bodyMedium)
        }
        f.horizons.forEach { h ->
            SectionLabel(h.label)
            if (h.opportunities.isEmpty()) {
                Text("—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                h.opportunities.forEach { op ->
                    AiAssetRow(
                        symbol = op.symbol,
                        name = op.name,
                        pct = op.potentialPct,
                        note = op.rationale,
                        tracked = op.symbol in tracked,
                        onAdd = { onAdd(op.symbol) }
                    )
                }
            }
        }
        AiSources(f.sources)
        if (generatedAt != null) {
            Text(
                "Généré ${timeAgo(generatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onRefresh, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("Actualiser les opportunités")
        }
    }
}

/**
 * Downside-risk radar: among the assets the user already tracks, the ones most at risk of a
 * decline. The mirror of [OpportunitiesCard] (which surfaces untracked upside).
 */
@Composable
private fun RisksCard(vm: MarketViewModel) {
    val settings by vm.settings.collectAsState()

    // Run-state lives in the ViewModel so it survives tab switches, navigation and restart.
    val state by vm.riskState.collectAsState()
    val cached = vm.aiRisk.collectAsState().value

    fun run() = vm.runRisk()

    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Risques IA — vos actifs suivis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Parmi les actifs de votre liste de suivi, ceux qui présentent un risque élevé de "
                    + "BAISSE à court/moyen terme, d'après les analyses et actualités du marché. "
                    + "Scénarios spéculatifs, pas un conseil en investissement.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (settings.anthropicApiKey.isBlank()) {
                Text(
                    "Ajoutez votre clé API Anthropic dans Réglages pour analyser les risques.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                when (val s = state) {
                    AiUiState.Idle -> OutlinedButton(onClick = { run() }, modifier = Modifier.fillMaxWidth()) {
                        Text("⚠️  Analyser les risques")
                    }
                    AiUiState.Loading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Analyse des risques sur le web…", style = MaterialTheme.typography.bodyMedium)
                    }
                    is AiUiState.Error -> AiErrorRow(s.message) { run() }
                    is AiUiState.Success -> RiskContent(
                        r = s.data,
                        generatedAt = cached?.ts,
                        onRefresh = { run() }
                    )
                }
                AiDisclaimer()
            }
        }
    }
}

@Composable
private fun RiskContent(
    r: RiskAdvice,
    generatedAt: Long?,
    onRefresh: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (r.warnings.isEmpty()) {
            Text(
                "Aucun risque marquant détecté sur vos actifs suivis.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            r.warnings.forEach { w ->
                RiskWarningRow(
                    symbol = w.symbol,
                    name = w.name,
                    downsidePct = w.downsidePct,
                    severity = w.severity,
                    rationale = w.rationale
                )
            }
        }
        AiSources(r.sources)
        if (generatedAt != null) {
            Text(
                "Généré ${timeAgo(generatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onRefresh, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("Actualiser les risques")
        }
    }
}
