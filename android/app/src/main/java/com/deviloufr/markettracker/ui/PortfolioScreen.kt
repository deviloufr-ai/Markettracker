package com.deviloufr.markettracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.deviloufr.markettracker.data.BoursoCsvParser
import com.deviloufr.markettracker.data.PortfolioMath
import com.deviloufr.markettracker.data.Position
import com.deviloufr.markettracker.data.Trade
import com.deviloufr.markettracker.data.TradeSide
import com.deviloufr.markettracker.data.sideLabelFr
import com.deviloufr.markettracker.ui.theme.MarketTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** MIME types BoursoBank exports (CSV or XLSX) have been seen labelled as (providers vary). */
private val IMPORT_MIME = arrayOf(
    "text/csv", "text/comma-separated-values", "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "text/plain", "application/octet-stream", "*/*"
)

private data class EditorSeed(val symbol: String, val side: TradeSide)

@Composable
fun PortfolioScreen(vm: MarketViewModel, onPositionClick: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val trades by vm.trades.collectAsState()
    val quotes by vm.quotes.collectAsState()

    val positions = remember(trades) { PortfolioMath.positionsFrom(trades) }
    val open = positions.filter { it.isOpen }

    var editor by remember { mutableStateOf<EditorSeed?>(null) }
    var importing by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ImportResult?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    // Warm quotes for held symbols whenever the set of positions changes.
    LaunchedEffect(open.map { it.symbol }.toSet()) {
        if (open.isNotEmpty()) vm.refreshPortfolioQuotes(open.map { it.symbol })
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = true
            scope.launch {
                val res = vm.previewBoursoCsv(uri)
                importing = false
                if (res.rows.isEmpty()) message = res.error ?: "Aucune transaction trouvée dans ce fichier."
                else preview = res
            }
        }
    }

    // Portfolio totals — cost is only counted for positions whose live price is known,
    // so value and cost stay on the same basis.
    var totalValue = 0.0
    var totalCost = 0.0
    var totalRealized = 0.0
    positions.forEach { p ->
        totalRealized += p.realizedPnl
        val price = quotes[p.symbol]?.price
        if (p.isOpen && price != null) {
            totalValue += p.marketValue(price)
            totalCost += p.invested
        }
    }
    val totalUnrealized = totalValue - totalCost
    val totalUnrealizedPct = if (totalCost > 0.0) totalUnrealized / totalCost * 100.0 else null

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PortfolioHero(
                openCount = open.count(),
                totalValue = totalValue,
                unrealized = totalUnrealized,
                unrealizedPct = totalUnrealizedPct,
                realized = totalRealized,
                onRefresh = { vm.refreshPortfolioQuotes(open.map { it.symbol }) },
                onOpenBourso = { BoursoBank.open(context) }
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { editor = EditorSeed("", TradeSide.BUY) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Transaction")
                }
                OutlinedButton(
                    onClick = { picker.launch(IMPORT_MIME) },
                    enabled = !importing,
                    modifier = Modifier.weight(1f)
                ) {
                    if (importing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text("  Importer")
                }
            }
        }
        item {
            Text(
                "Importez un export BoursoBank (CSV ou Excel) : un relevé de positions charge vos " +
                    "lignes au PRU, un historique d'opérations charge vos ordres. Les achats/ventes " +
                    "réels se passent dans BoursoBank ; l'app suit vos positions et le P&L en direct.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (open.isNotEmpty()) {
            item { SectionLabel("Positions") }
            items(open, key = { it.symbol }) { position ->
                PositionCard(
                    position = position,
                    price = quotes[position.symbol]?.price,
                    onClick = { onPositionClick(position.symbol) },
                    onBuy = { editor = EditorSeed(position.symbol, TradeSide.BUY) },
                    onSell = { editor = EditorSeed(position.symbol, TradeSide.SELL) }
                )
            }
        }

        if (trades.isNotEmpty()) {
            item { SectionLabel("Historique") }
            items(trades.take(50), key = { it.id }) { trade ->
                TradeRow(trade = trade, onDelete = { vm.removeTrade(trade.id) })
            }
        }

        if (trades.isEmpty()) {
            item {
                EmptyState(
                    "Aucune transaction",
                    "Ajoutez un achat/vente, ou importez un export CSV BoursoBank pour suivre votre portefeuille."
                )
            }
        }
    }

    editor?.let { seed ->
        TradeEditorDialog(
            initialSymbol = seed.symbol,
            initialSide = seed.side,
            onDismiss = { editor = null },
            onSave = { vm.addTrade(it); editor = null }
        )
    }

    preview?.let { result ->
        ImportPreviewDialog(
            result = result,
            onDismiss = { preview = null },
            onConfirm = {
                vm.importTrades(result.rows.map { it.trade }, result.snapshot)
                preview = null
                val kind = if (result.snapshot) "position(s)" else "transaction(s)"
                message = "${result.rows.size} $kind importée(s)."
            }
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
            title = { Text("Import BoursoBank") },
            text = { Text(text) }
        )
    }
}

@Composable
private fun PortfolioHero(
    openCount: Int,
    totalValue: Double,
    unrealized: Double,
    unrealizedPct: Double?,
    realized: Double,
    onRefresh: () -> Unit,
    onOpenBourso: () -> Unit
) {
    GradientHeroCard {
        Text(
            "Mon portefeuille",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.85f)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            fmtPrice(totalValue),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        val pnlColor = if (unrealized >= 0) Color(0xFFBBF7D0) else Color(0xFFFECACA)
        Text(
            buildString {
                append(if (unrealized >= 0) "▲ " else "▼ ")
                append(fmtSigned(unrealized))
                unrealizedPct?.let { append("  (${fmtPct(it)})") }
                append(" latent")
            },
            style = MaterialTheme.typography.titleSmall,
            color = pnlColor
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "$openCount position${if (openCount > 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f)
            )
            if (realized != 0.0) {
                Text(
                    "Réalisé ${fmtSigned(realized)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroActionButton(
                icon = Icons.Filled.Refresh,
                label = "Actualiser",
                onClick = onRefresh
            )
            HeroActionButton(
                icon = Icons.Filled.OpenInNew,
                label = "Ordre sur BoursoBank",
                modifier = Modifier.weight(1f),
                onClick = onOpenBourso
            )
        }
    }
}

@Composable
private fun HeroActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
        }
    }
}

@Composable
private fun PositionCard(
    position: Position,
    price: Double?,
    onClick: () -> Unit,
    onBuy: () -> Unit,
    onSell: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssetLogo(symbol = position.symbol, size = 40.dp, shape = RoundedCornerShape(12.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(position.symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${fmtQty(position.quantity)} × ${fmtPrice(position.avgCost)} (PRU)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (price != null) fmtPrice(position.marketValue(price)) else "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    val pct = price?.let { position.unrealizedPct(it) }
                    if (pct != null) PctBadge(pct, modifier = Modifier.padding(top = 2.dp))
                }
            }
            if (price != null) {
                val pnl = position.unrealizedPnl(price)
                Text(
                    "P&L latent ${fmtSigned(pnl)}  ·  cours ${fmtPrice(price)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (pnl >= 0) MarketTheme.colors.gain else MarketTheme.colors.loss
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onBuy, modifier = Modifier.weight(1f)) { Text("Acheter") }
                OutlinedButton(onClick = onSell, modifier = Modifier.weight(1f)) { Text("Vendre") }
            }
        }
    }
}

@Composable
private fun TradeRow(trade: Trade, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tint = if (trade.side == TradeSide.BUY) MarketTheme.colors.gain else MarketTheme.colors.loss
            Text(
                sideLabelFr(trade.side).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tint,
                modifier = Modifier.size(width = 52.dp, height = 20.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(trade.symbol, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${fmtQty(trade.quantity)} × ${fmtPrice(trade.price)}  ·  ${fmtDate(trade.ts)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                fmtPrice(trade.quantity * trade.price),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun TradeEditorDialog(
    initialSymbol: String,
    initialSide: TradeSide,
    onDismiss: () -> Unit,
    onSave: (Trade) -> Unit
) {
    var symbol by remember { mutableStateOf(initialSymbol) }
    var side by remember { mutableStateOf(initialSide) }
    var quantity by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var fees by remember { mutableStateOf("") }
    val today = remember { SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date()) }
    var date by remember { mutableStateOf(today) }

    val qtyVal = BoursoCsvParser.parseNumber(quantity)
    val priceVal = BoursoCsvParser.parseNumber(price)
    val valid = symbol.isNotBlank() && qtyVal != null && qtyVal > 0.0 && priceVal != null && priceVal >= 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TradeSide.entries.forEach { s ->
                        FilterChip(
                            selected = side == s,
                            onClick = { side = s },
                            label = { Text(sideLabelFr(s)) }
                        )
                    }
                }
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it.uppercase().trim() },
                    label = { Text("Symbole (ex. AAPL, MC.PA)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Quantité") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Prix unitaire") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fees,
                        onValueChange = { fees = it },
                        label = { Text("Frais (opt.)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date (jj/mm/aaaa)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        Trade(
                            id = UUID.randomUUID().toString(),
                            symbol = symbol.trim().uppercase(),
                            side = side,
                            quantity = qtyVal ?: 0.0,
                            price = priceVal ?: 0.0,
                            ts = BoursoCsvParser.parseDate(date) ?: System.currentTimeMillis(),
                            fees = BoursoCsvParser.parseNumber(fees) ?: 0.0,
                            note = ""
                        )
                    )
                }
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun ImportPreviewDialog(
    result: ImportResult,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val unresolved = result.rows.count { !it.resolved }
    val kind = if (result.snapshot) "position(s)" else "transaction(s)"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importer ${result.rows.size} $kind") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (result.snapshot) {
                    Text(
                        "Relevé de positions : chargé au prix de revient (PRU). Remplace un import " +
                            "de positions précédent ; vos saisies manuelles sont conservées.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (result.skipped > 0 || unresolved > 0) {
                    Text(
                        buildString {
                            if (result.skipped > 0) append("${result.skipped} ligne(s) ignorée(s). ")
                            if (unresolved > 0) append("$unresolved symbole(s) non résolu(s) (pas de cours en direct).")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(result.rows, key = { it.trade.id }) { row ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val tint = if (row.trade.side == TradeSide.BUY) MarketTheme.colors.gain else MarketTheme.colors.loss
                            Text(
                                sideLabelFr(row.trade.side).take(1),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = tint,
                                modifier = Modifier.size(width = 16.dp, height = 18.dp)
                            )
                            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                                Text(
                                    if (row.resolved) row.trade.symbol else "${row.rawSymbol} ⚠︎",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    row.label.take(28),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "${fmtQty(row.trade.quantity)} × ${fmtPrice(row.trade.price)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Importer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

// --- Small formatting helpers local to the portfolio -----------------------------------------

private fun fmtSigned(value: Double): String = (if (value >= 0) "+" else "") + fmtPrice(value)

private fun fmtQty(q: Double): String =
    if (q == Math.floor(q)) q.toLong().toString()
    else String.format(Locale.US, "%.4f", q).trimEnd('0').trimEnd('.')

private fun fmtDate(ts: Long): String =
    SimpleDateFormat("dd/MM/yy", Locale.FRANCE).format(Date(ts))
