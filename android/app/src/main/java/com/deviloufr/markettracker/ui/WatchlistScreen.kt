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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.deviloufr.markettracker.data.PricePoint
import com.deviloufr.markettracker.data.Quote

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
    val sparklines by vm.sparklines.collectAsState()

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
                onStart = { vm.startMonitoring(context) },
                onStop = { vm.stopMonitoring(context) },
                onRefresh = { vm.refreshOnce() }
            )
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
 * Gradient hero fusing the monitoring status and the watchlist snapshot.
 * Monitoring start/stop and a manual refresh live in the action row; the AI
 * brief now has its own dedicated "Analyse IA" tab.
 */
@Composable
private fun HeroCard(
    monitoring: Boolean,
    assetCount: Int,
    up: Int,
    down: Int,
    best: Pair<String, Double>?,
    worst: Pair<String, Double>?,
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
