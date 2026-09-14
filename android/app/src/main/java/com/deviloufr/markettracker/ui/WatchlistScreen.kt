package com.deviloufr.markettracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import com.deviloufr.markettracker.data.MarketBrief
import com.deviloufr.markettracker.data.Quote
import com.deviloufr.markettracker.ui.theme.Gain
import com.deviloufr.markettracker.ui.theme.Loss
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

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MonitorCard(
                monitoring = monitoring,
                tickerCount = watchlist.size,
                onStart = { vm.startMonitoring(context) },
                onStop = { vm.stopMonitoring(context) },
                onRefresh = { vm.refreshOnce() }
            )
        }
        if (watchlist.isNotEmpty()) {
            item { MarketBriefCard(vm = vm, watchlist = watchlist, quotes = quotes) }
        }
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
            TickerCard(
                symbol = symbol,
                quote = quotes[symbol],
                onClick = { onTickerClick(symbol) },
                onRemove = { vm.removeTicker(symbol) }
            )
        }
        if (watchlist.isEmpty()) {
            item { EmptyState("Aucun symbole", "Ajoutez un symbole comme AAPL ou TSLA pour commencer le suivi.") }
        }
    }
}

@Composable
private fun MonitorCard(
    monitoring: Boolean,
    tickerCount: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (monitoring) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (monitoring) "● Surveillance active" else "○ Surveillance arrêtée",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (monitoring) "Surveillance de $tickerCount actifs en arrière-plan. Vous recevrez une notification à chaque alerte."
                else "Démarrez la surveillance pour suivre les prix et recevoir des alertes natives + WhatsApp.",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (monitoring) {
                    Button(onClick = onStop) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Text("  Arrêter")
                    }
                } else {
                    Button(onClick = onStart) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Text("  Démarrer")
                    }
                }
                OutlinedButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text("  Actualiser")
                }
            }
        }
    }
}

@Composable
private fun TickerCard(symbol: String, quote: Quote?, onClick: () -> Unit, onRemove: () -> Unit) {
    val market = marketOf(symbol)
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssetLogo(symbol = symbol, size = 40.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val sub = when {
                    quote == null -> "${market.label} · Aucune donnée"
                    else -> buildString {
                        append(market.label)
                        append(" · ")
                        append(timeAgo(quote.ts))
                        quote.volume?.let { append(" · vol ${fmtVolume(it)}") }
                    }
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (quote != null) fmtPrice(quote.price) else "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val change = quote?.dayChangePct
                if (change != null) {
                    Text(
                        fmtPct(change),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (change >= 0) Gain else Loss,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Supprimer $symbol", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun MarketBriefCard(
    vm: MarketViewModel,
    watchlist: List<String>,
    quotes: Map<String, Quote>
) {
    val settings by vm.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var brief by remember { mutableStateOf<AiUiState<MarketBrief>>(AiUiState.Idle) }

    fun runBrief() {
        brief = AiUiState.Loading
        scope.launch {
            brief = vm.marketBrief().fold(
                onSuccess = { AiUiState.Success(it) },
                onFailure = { AiUiState.Error(it.message ?: "Brief indisponible.") }
            )
        }
    }

    // Free on-device snapshot from the current quotes we already hold.
    val rated = watchlist.mapNotNull { s -> quotes[s]?.dayChangePct?.let { s to it } }
    val up = rated.count { it.second >= 0.0 }
    val down = rated.size - up
    val best = rated.maxByOrNull { it.second }
    val worst = rated.minByOrNull { it.second }

    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Brief du marché IA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            if (rated.isEmpty()) {
                Text(
                    "Actualisez les cours pour voir l'instantané de votre liste.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "$up en hausse · $down en baisse",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    best?.let {
                        Text("↑ ${it.first} ${fmtPct(it.second)}", style = MaterialTheme.typography.bodySmall, color = Gain)
                    }
                    worst?.takeIf { it.first != best?.first }?.let {
                        Text("↓ ${it.first} ${fmtPct(it.second)}", style = MaterialTheme.typography.bodySmall, color = Loss)
                    }
                }
            }

            if (settings.anthropicApiKey.isBlank()) {
                Text(
                    "Ajoutez votre clé API Anthropic dans Réglages pour un brief rédigé par l'IA, "
                        + "avec le contexte de marché du jour issu du web.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                when (val s = brief) {
                    AiUiState.Idle -> OutlinedButton(onClick = { runBrief() }, modifier = Modifier.fillMaxWidth()) {
                        Text("🔮  Générer le brief IA")
                    }
                    AiUiState.Loading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Analyse du marché sur le web…", style = MaterialTheme.typography.bodyMedium)
                    }
                    is AiUiState.Error -> AiErrorRow(s.message) { runBrief() }
                    is AiUiState.Success -> MarketBriefContent(s.data, onRefresh = { runBrief() })
                }
                AiDisclaimer()
            }
        }
    }
}

@Composable
private fun MarketBriefContent(b: MarketBrief, onRefresh: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DirectionPill(b.sentiment, b.sentiment.replaceFirstChar { it.uppercase() })
        if (b.summary.isNotBlank()) Text(b.summary, style = MaterialTheme.typography.bodyMedium)
        if (b.highlights.isNotEmpty()) BulletList(b.highlights)
        AiSources(b.sources)
        TextButton(onClick = onRefresh, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("Actualiser le brief")
        }
    }
}
