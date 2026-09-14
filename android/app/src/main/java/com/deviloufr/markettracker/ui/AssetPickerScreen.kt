package com.deviloufr.markettracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.deviloufr.markettracker.data.Asset
import com.deviloufr.markettracker.data.AssetCatalog
import kotlinx.coroutines.delay

/** Market display order in browse mode: most-used categories first. */
private val MARKET_ORDER = listOf(
    Market.ACTION, Market.CRYPTO, Market.INDICE, Market.FOREX, Market.FUTURES
)

/** A rendered line in the picker list: a header, a sub-header, an asset, or the custom-add row. */
private sealed interface PickerRow {
    val key: String

    data class Section(val title: String, val market: Market?) : PickerRow {
        override val key get() = "sec_$title"
    }

    data class SubSection(val title: String) : PickerRow {
        override val key get() = "sub_$title"
    }

    data class Item(val asset: Asset) : PickerRow {
        override val key get() = "item_${asset.symbol}"
    }

    data class Custom(val symbol: String) : PickerRow {
        override val key get() = "custom"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetPickerScreen(vm: MarketViewModel, onBack: () -> Unit) {
    val watchlist by vm.watchlist.collectAsState()
    val watchlistSet = remember(watchlist) { watchlist.map { it.uppercase() }.toSet() }

    var query by remember { mutableStateOf("") }
    var selectedMarket by remember { mutableStateOf<Market?>(null) }
    var remote by remember { mutableStateOf<List<Asset>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }

    // Debounced live Yahoo search: fires 350 ms after the user stops typing.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            remote = emptyList(); searching = false; searched = false
            return@LaunchedEffect
        }
        searching = true; searched = false
        delay(350)
        remote = vm.searchAssets(q)
        searching = false; searched = true
    }

    val rows = buildRows(query, selectedMarket, remote, searched)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajouter des actifs", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                onClear = { query = "" }
            )
            CategoryChips(selected = selectedMarket, onSelect = { selectedMarket = it })

            if (rows.isEmpty() && !searching) {
                EmptyState(
                    "Aucun résultat",
                    "Essayez un autre terme, ou saisissez un symbole Yahoo (ex. AAPL, BTC-USD)."
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
            ) {
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is PickerRow.Section -> SectionHeader(row.title, row.market)
                        is PickerRow.SubSection -> SubSectionHeader(row.title)
                        is PickerRow.Item -> {
                            val stored = row.asset.symbol.trim().uppercase()
                            AssetRow(
                                asset = row.asset,
                                selected = stored in watchlistSet,
                                onToggle = {
                                    if (stored in watchlistSet) vm.removeTicker(stored)
                                    else vm.addTicker(stored)
                                }
                            )
                        }
                        is PickerRow.Custom -> CustomAddRow(
                            symbol = row.symbol,
                            onAdd = { vm.addTicker(row.symbol) }
                        )
                    }
                }
                if (searching) {
                    item(key = "loading") {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                "  Recherche…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Turn the current query / filter / remote-results into a flat, ordered list of
 * rows. Blank query → curated catalog grouped by market (Actions gets
 * sub-sections); otherwise curated matches followed by a "Résultats Yahoo"
 * section and, if nothing matches a single-token query, a custom-add row.
 */
private fun buildRows(
    query: String,
    selectedMarket: Market?,
    remote: List<Asset>,
    searched: Boolean
): List<PickerRow> {
    val rows = mutableListOf<PickerRow>()
    val q = query.trim()

    if (q.isEmpty()) {
        val markets = selectedMarket?.let { listOf(it) } ?: MARKET_ORDER
        for (market in markets) {
            val assets = AssetCatalog.all.filter { marketOf(it.symbol) == market }
            if (assets.isEmpty()) continue
            rows += PickerRow.Section(market.label, market)
            // Preserve catalog order; only Actions carry sub-group labels.
            var lastGroup: String? = null
            var first = true
            assets.forEach { a ->
                if (a.group != null && (first || a.group != lastGroup)) {
                    rows += PickerRow.SubSection(a.group)
                }
                lastGroup = a.group
                first = false
                rows += PickerRow.Item(a)
            }
        }
        return rows
    }

    // Search mode.
    val local = AssetCatalog.filter(q)
        .filterByMarket(selectedMarket)
    if (local.isNotEmpty()) {
        rows += PickerRow.Section("Sélection", null)
        local.forEach { rows += PickerRow.Item(it) }
    }

    val localSymbols = local.map { it.symbol.uppercase() }.toSet()
    val remoteFiltered = remote
        .filter { it.symbol.uppercase() !in localSymbols }
        .filterByMarket(selectedMarket)
    if (remoteFiltered.isNotEmpty()) {
        rows += PickerRow.Section("Résultats Yahoo", null)
        remoteFiltered.forEach { rows += PickerRow.Item(it) }
    }

    // Offer a raw-symbol add when a single-token query matched nothing exactly.
    val upper = q.uppercase()
    val exactMatch = (local + remoteFiltered).any { it.symbol.uppercase() == upper }
    if (searched && !exactMatch && !q.contains(' ') && q.length <= 15) {
        rows += PickerRow.Custom(upper)
    }

    return rows
}

private fun List<Asset>.filterByMarket(market: Market?): List<Asset> =
    if (market == null) this else filter { marketOf(it.symbol) == market }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, onClear: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        label = { Text("Rechercher un actif ou un symbole") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Close, contentDescription = "Effacer")
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Search
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryChips(selected: Market?, onSelect: (Market?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("Tous") }
        )
        MARKET_ORDER.forEach { market ->
            FilterChip(
                selected = selected == market,
                onClick = { onSelect(if (selected == market) null else market) },
                label = { Text(market.label) },
                leadingIcon = {
                    Icon(market.icon, contentDescription = null, tint = market.tint, modifier = Modifier.size(18.dp))
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, market: Market?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (market != null) {
            Icon(market.icon, contentDescription = null, tint = market.tint, modifier = Modifier.size(18.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SubSectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun AssetRow(asset: Asset, selected: Boolean, onToggle: () -> Unit) {
    val market = marketOf(asset.symbol)
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(market.tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(market.icon, contentDescription = market.label, tint = market.tint)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(asset.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "${asset.symbol} · ${market.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.AddCircleOutline,
            contentDescription = if (selected) "Retirer ${asset.symbol}" else "Ajouter ${asset.symbol}",
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun CustomAddRow(symbol: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onAdd).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.AddCircleOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            "  Ajouter « $symbol » comme symbole",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}
