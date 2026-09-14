package com.deviloufr.markettracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class Tab(val label: String, val icon: ImageVector) {
    WATCHLIST("Watchlist", Icons.Filled.ShowChart),
    ALERTS("Alerts", Icons.Filled.Notifications),
    SETTINGS("Settings", Icons.Filled.Settings)
}

@Composable
fun AppScaffold(vm: MarketViewModel = viewModel()) {
    var tab by remember { mutableStateOf(Tab.WATCHLIST) }
    val alerts by vm.alerts.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            if (t == Tab.ALERTS && alerts.isNotEmpty()) {
                                BadgedBox(badge = { Badge { Text(alerts.size.toString()) } }) {
                                    Icon(t.icon, contentDescription = t.label)
                                }
                            } else {
                                Icon(t.icon, contentDescription = t.label)
                            }
                        },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.WATCHLIST -> WatchlistScreen(vm)
                Tab.ALERTS -> AlertsScreen(vm)
                Tab.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
}
