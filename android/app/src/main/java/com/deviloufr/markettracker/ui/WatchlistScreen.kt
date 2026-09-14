package com.deviloufr.markettracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.deviloufr.markettracker.data.Quote
import com.deviloufr.markettracker.ui.theme.Gain
import com.deviloufr.markettracker.ui.theme.Loss

@Composable
fun WatchlistScreen(vm: MarketViewModel) {
    val context = LocalContext.current
    val watchlist by vm.watchlist.collectAsState()
    val quotes by vm.quotes.collectAsState()
    val monitoring by vm.monitoring.collectAsState()
    var newSymbol by remember { mutableStateOf("") }

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
        item {
            AddTickerRow(
                value = newSymbol,
                onValueChange = { newSymbol = it },
                onAdd = {
                    vm.addTicker(newSymbol)
                    newSymbol = ""
                }
            )
        }
        items(watchlist, key = { it }) { symbol ->
            TickerCard(symbol = symbol, quote = quotes[symbol], onRemove = { vm.removeTicker(symbol) })
        }
        if (watchlist.isEmpty()) {
            item { EmptyState("No tickers yet", "Add a symbol like AAPL or TSLA to start tracking.") }
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
                if (monitoring) "● Monitoring active" else "○ Monitoring stopped",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (monitoring) "Watching $tickerCount tickers in the background. You'll get a notification on each alert."
                else "Start monitoring to poll prices and receive native + WhatsApp alerts.",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (monitoring) {
                    Button(onClick = onStop) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Text("  Stop")
                    }
                } else {
                    Button(onClick = onStart) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Text("  Start")
                    }
                }
                OutlinedButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text("  Refresh")
                }
            }
        }
    }
}

@Composable
private fun AddTickerRow(value: String, onValueChange: (String) -> Unit, onAdd: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Add ticker") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done
            )
        )
        FilledTonalButton(onClick = onAdd, enabled = value.isNotBlank()) {
            Icon(Icons.Filled.Add, contentDescription = "Add")
        }
    }
}

@Composable
private fun TickerCard(symbol: String, quote: Quote?, onRemove: () -> Unit) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val sub = when {
                    quote == null -> "No data yet"
                    else -> buildString {
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
                Icon(Icons.Filled.Delete, contentDescription = "Remove $symbol", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
