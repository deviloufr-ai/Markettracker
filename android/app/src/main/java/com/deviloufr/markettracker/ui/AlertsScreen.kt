package com.deviloufr.markettracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deviloufr.markettracker.data.AlertEvent
import com.deviloufr.markettracker.ui.theme.Gain
import com.deviloufr.markettracker.ui.theme.Loss

@Composable
fun AlertsScreen(vm: MarketViewModel) {
    val alerts by vm.alerts.collectAsState()

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Alert history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (alerts.isNotEmpty()) {
                TextButton(onClick = { vm.clearAlerts() }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                    Text("  Clear")
                }
            }
        }

        if (alerts.isEmpty()) {
            EmptyState("No alerts yet", "Alerts appear here when a ticker crosses your thresholds while monitoring is on.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
            ) {
                items(alerts, key = { "${it.symbol}-${it.reason}-${it.ts}" }) { alert ->
                    AlertRow(alert)
                }
            }
        }
    }
}

@Composable
private fun AlertRow(alert: AlertEvent) {
    val isUp = !alert.detail.contains("🔻")
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(alert.symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    timeAgo(alert.ts),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                alert.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = if (alert.reason == "price_move") (if (isUp) Gain else Loss) else MaterialTheme.colorScheme.onSurface
            )
            Text(
                "at ${fmtPrice(alert.price)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
