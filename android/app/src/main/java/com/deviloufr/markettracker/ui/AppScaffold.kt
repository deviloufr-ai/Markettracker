package com.deviloufr.markettracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deviloufr.markettracker.update.UpdateState

private enum class Tab(val label: String, val icon: ImageVector) {
    WATCHLIST("Suivi", Icons.Filled.ShowChart),
    AI("Analyse IA", Icons.Filled.AutoAwesome),
    PORTFOLIO("Portefeuille", Icons.Filled.AccountBalanceWallet),
    ALERTS("Alertes", Icons.Filled.Notifications),
    SETTINGS("Réglages", Icons.Filled.Settings)
}

@Composable
fun AppScaffold(vm: MarketViewModel = viewModel()) {
    var tab by remember { mutableStateOf(Tab.WATCHLIST) }
    var detailSymbol by remember { mutableStateOf<String?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    val alerts by vm.alerts.collectAsState()

    // Check the releases channel for a newer build every time the app comes to
    // the foreground (cold start and each resume), not just on first composition.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                // Re-check on each foreground, but don't re-nag while the user is
                // still looking at (or just closed) the banner for a known update.
                val suppressed = vm.updateDismissed.value && vm.update.value is UpdateState.Available
                if (!suppressed) vm.checkForUpdate()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // A symbol detail screen is shown on top of the tabs when one is selected.
    detailSymbol?.let { symbol ->
        BackHandler { detailSymbol = null }
        TickerDetailScreen(vm = vm, symbol = symbol, onBack = { detailSymbol = null })
        return
    }

    // The asset picker is shown on top of the tabs while adding assets.
    if (showPicker) {
        BackHandler { showPicker = false }
        AssetPickerScreen(vm = vm, onBack = { showPicker = false })
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
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
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Update banner sits above the tab content, across every tab.
            UpdateBanner(vm)
            Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.WATCHLIST -> WatchlistScreen(
                        vm,
                        onTickerClick = { detailSymbol = it },
                        onAddAssets = { showPicker = true }
                    )
                    Tab.AI -> AiScreen(vm, onAddAssets = { showPicker = true })
                    Tab.PORTFOLIO -> PortfolioScreen(vm, onPositionClick = { detailSymbol = it })
                    Tab.ALERTS -> AlertsScreen(vm)
                    Tab.SETTINGS -> SettingsScreen(vm)
                }
            }
        }
    }
}
