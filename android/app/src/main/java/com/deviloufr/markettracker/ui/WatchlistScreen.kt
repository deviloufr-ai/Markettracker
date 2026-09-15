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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deviloufr.markettracker.data.ForecastAdvice
import com.deviloufr.markettracker.data.MarketBrief
import com.deviloufr.markettracker.data.PricePoint
import com.deviloufr.markettracker.data.Quote
import kotlinx.coroutines.launch

@Composable
fun WatchlistScreen(
    vm: MarketViewModel,
    onTickerClick: (String) -> Unit = {},
    onAddAssets: () -> Unit = {}
) {
    val context = LocalContext.current
    val watchlist by vm.watchlist.collectAsState()
    val quotes by vm.quotes.collectAsState()
    val monitoring by vm.monitoring.collectAsState()
    val settings by vm.settings.collectAsState()
    val sparklines by vm.sparklines.collectAsState()
    val scope = rememberCoroutineScope()

    // AI brief state is hoisted here so the hero's trigger and the results section share it.
    var brief by remember { mutableStateOf<AiUiState<MarketBrief>>(AiUiState.Idle) }
    val cachedBrief = vm.aiBrief.collectAsState().value
    LaunchedEffect(cachedBrief) {
        if (brief is AiUiState.Idle && cachedBrief != null) brief = AiUiState.Success(cachedBrief.brief)
    }

    fun runBrief() {
        brief = AiUiState.Loading
        scope.launch {
            brief = vm.marketBrief().fold(
                onSuccess = { AiUiState.Success(it) },
                onFailure = { AiUiState.Error(it.message ?: "Brief indisponible.") }
            )
        }
    }

    // Free on-device snapshot from the quotes we already hold.
    val rated = watchlist.mapNotNull { s -> quotes[s]?.dayChangePct?.let { s to it } }
    val up = rated.count { it.second >= 0.0 }
    val down = rated.size - up
    val best = rated.maxByOrNull { it.second }
    val worst = rated.minByOrNull { it.second }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            HeroCard(
                monitoring = monitoring,
                assetCount = watchlist.size,
                up = up,
                down = down,
                best = best,
                worst = worst,
                aiEnabled = settings.anthropicApiKey.isNotBlank(),
                briefLoading = brief is AiUiState.Loading,
                hasBrief = brief is AiUiState.Success,
                onGenerate = { runBrief() },
                onStart = { vm.startMonitoring(context) },
                onStop = { vm.stopMonitoring(context) },
                onRefresh = { vm.refreshOnce() }
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
                    onRetry = { runBrief() }
                )
            }
        }
        item { OpportunitiesCard(vm = vm, watchlist = watchlist) }
        item {
            FilledTonalButton(
                onClick = onAddAssets,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  Ajouter des actifs")
            }
        }
        items(watchlist, key = { it }) { symbol ->
            LaunchedEffect(symbol) { vm.loadSparkline(symbol) }
            TickerCard(
                symbol = symbol,
                quote = quotes[symbol],
                sparkline = sparklines[symbol],
                onClick = { onTickerClick(symbol) },
                onRemove = { vm.removeTicker(symbol) }
            )
        }
        if (watchlist.isEmpty()) {
            item { EmptyState("Aucun symbole", "Ajoutez un symbole comme AAPL ou TSLA pour commencer le suivi.") }
        }
    }
}

/**
 * Gradient hero fusing the monitoring status, the portfolio snapshot, and the
 * AI-brief trigger. Monitoring start/stop stays reachable via the play/stop
 * button in the action row.
 */
@Composable
private fun HeroCard(
    monitoring: Boolean,
    assetCount: Int,
    up: Int,
    down: Int,
    best: Pair<String, Double>?,
    worst: Pair<String, Double>?,
    aiEnabled: Boolean,
    briefLoading: Boolean,
    hasBrief: Boolean,
    onGenerate: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRefresh: () -> Unit
) {
    GradientHeroCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Suivi du marché",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.85f)
            )
            StatusChip(monitoring)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "$assetCount actifs",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            if (up + down > 0) {
                Text(
                    "$up en hausse",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFFBBF7D0),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
        if (best != null || worst != null) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                best?.let {
                    Text("↑ ${it.first} ${fmtPct(it.second)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFDCFCE7))
                }
                worst?.takeIf { it.first != best?.first }?.let {
                    Text("↓ ${it.first} ${fmtPct(it.second)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFECACA))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroIconButton(
                icon = if (monitoring) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = if (monitoring) "Arrêter la surveillance" else "Démarrer la surveillance",
                onClick = { if (monitoring) onStop() else onStart() }
            )
            HeroIconButton(
                icon = Icons.Filled.Refresh,
                contentDescription = "Actualiser les cours",
                onClick = onRefresh
            )
            if (aiEnabled) {
                HeroBriefButton(
                    loading = briefLoading,
                    hasBrief = hasBrief,
                    modifier = Modifier.weight(1f),
                    onClick = onGenerate
                )
            }
        }
    }
}

@Composable
private fun StatusChip(active: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (active) Color(0xFF4ADE80) else Color(0xFFE2E8F0))
        )
        Text(
            if (active) "Surveillance active" else "Surveillance arrêtée",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White
        )
    }
}

@Composable
private fun HeroIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White)
    }
}

@Composable
private fun HeroBriefButton(loading: Boolean, hasBrief: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.16f))
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

@Composable
private fun TickerCard(
    symbol: String,
    quote: Quote?,
    sparkline: List<PricePoint>?,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssetLogo(symbol = symbol, size = 40.dp, shape = RoundedCornerShape(12.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    displayName(symbol, quote),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        symbol,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                    Text(
                        exchangeLabel(symbol, quote),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            val change = quote?.dayChangePct
            if (sparkline != null && sparkline.size >= 2) {
                Sparkline(
                    points = sparkline,
                    positive = (change ?: 0.0) >= 0.0,
                    modifier = Modifier.padding(horizontal = 8.dp).size(width = 52.dp, height = 26.dp)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (quote != null) fmtPrice(quote.price) else "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (change != null) {
                    PctBadge(change, modifier = Modifier.padding(top = 2.dp))
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Supprimer $symbol", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/** AI brief results, rendered below the hero (the trigger button lives in the hero). */
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
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<AiUiState<ForecastAdvice>>(AiUiState.Idle) }

    // Restore the last generated forecast so it survives navigation/restart.
    val cached = vm.aiForecast.collectAsState().value
    LaunchedEffect(cached) {
        if (state is AiUiState.Idle && cached != null) state = AiUiState.Success(cached.forecast)
    }

    fun run() {
        state = AiUiState.Loading
        scope.launch {
            state = vm.forecastAdvice().fold(
                onSuccess = { AiUiState.Success(it) },
                onFailure = { AiUiState.Error(it.message ?: "Opportunités indisponibles.") }
            )
        }
    }

    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Opportunités IA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Fort potentiel de hausse estimé par horizon (1 semaine → 1 an), d'après les analyses "
                    + "et actualités du marché. Scénarios spéculatifs, pas un conseil en investissement.",
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
                        Text("🔮  Générer les opportunités")
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
