package com.deviloufr.markettracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deviloufr.markettracker.data.HistoryRange
import com.deviloufr.markettracker.data.PricePoint
import com.deviloufr.markettracker.ui.theme.Gain
import com.deviloufr.markettracker.ui.theme.Loss
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data object Empty : HistoryUiState
    data class Loaded(val points: List<PricePoint>) : HistoryUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TickerDetailScreen(vm: MarketViewModel, symbol: String, onBack: () -> Unit) {
    val market = marketOf(symbol)
    val quote = vm.quotes.collectAsState().value[symbol]

    var range by remember { mutableStateOf(HistoryRange.DAYS) }
    var state by remember { mutableStateOf<HistoryUiState>(HistoryUiState.Loading) }

    LaunchedEffect(symbol, range) {
        state = HistoryUiState.Loading
        val points = vm.fetchHistory(symbol, range)
        state = if (points.isEmpty()) HistoryUiState.Empty else HistoryUiState.Loaded(points)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(symbol, fontWeight = FontWeight.Bold)
                        Text(
                            market.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PriceHeader(
                price = quote?.price ?: (state as? HistoryUiState.Loaded)?.points?.lastOrNull()?.close,
                dayChangePct = quote?.dayChangePct,
                asOf = quote?.ts
            )

            RangeChips(selected = range, onSelect = { range = it })

            Card {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (val s = state) {
                        HistoryUiState.Loading -> CircularProgressIndicator()
                        HistoryUiState.Empty -> EmptyState(
                            "Aucun historique",
                            "Yahoo Finance n'a pas renvoyé de données pour cette période."
                        )
                        is HistoryUiState.Loaded -> HistoryChart(points = s.points, range = range)
                    }
                }
            }

            (state as? HistoryUiState.Loaded)?.let { RangeStats(it.points) }
        }
    }
}

@Composable
private fun PriceHeader(price: Double?, dayChangePct: Double?, asOf: Long?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            if (price != null) fmtPrice(price) else "—",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        val sub = buildString {
            if (dayChangePct != null) append("${fmtPct(dayChangePct)} aujourd'hui")
            if (asOf != null) {
                if (isNotEmpty()) append(" · ")
                append(timeAgo(asOf))
            }
        }
        if (sub.isNotEmpty()) {
            Text(
                sub,
                style = MaterialTheme.typography.bodyMedium,
                color = if ((dayChangePct ?: 0.0) >= 0) Gain else Loss,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeChips(selected: HistoryRange, onSelect: (HistoryRange) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HistoryRange.entries.forEach { r ->
            FilterChip(
                selected = selected == r,
                onClick = { onSelect(r) },
                label = { Text(r.label) }
            )
        }
    }
}

@Composable
private fun HistoryChart(points: List<PricePoint>, range: HistoryRange) {
    val lineColor = if ((points.lastOrNull()?.close ?: 0.0) >= (points.firstOrNull()?.close ?: 0.0)) Gain else Loss
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val minV = points.minOf { it.close }
    val maxV = points.maxOf { it.close }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Canvas(Modifier.fillMaxSize()) {
                val n = points.size
                val span = (maxV - minV).takeIf { it > 0.0 } ?: 1.0
                val w = size.width
                val h = size.height

                // Faint horizontal gridlines at 0 / 50 / 100 %.
                for (f in listOf(0f, 0.5f, 1f)) {
                    val y = h * f
                    drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(w, y), strokeWidth = 1f)
                }

                fun xAt(i: Int): Float = if (n <= 1) w / 2f else w * i / (n - 1)
                fun yAt(v: Double): Float = (((maxV - v) / span) * h).toFloat()

                val line = Path()
                points.forEachIndexed { i, p ->
                    val x = xAt(i)
                    val y = yAt(p.close)
                    if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
                }

                // Gradient fill under the line.
                val fill = Path().apply {
                    addPath(line)
                    lineTo(xAt(n - 1), h)
                    lineTo(xAt(0), h)
                    close()
                }
                drawPath(
                    fill,
                    brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.22f), Color.Transparent))
                )
                drawPath(
                    line,
                    color = lineColor,
                    style = Stroke(width = 2.5f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            Text(
                fmtPrice(maxV),
                modifier = Modifier.align(Alignment.TopStart),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor
            )
            Text(
                fmtPrice(minV),
                modifier = Modifier.align(Alignment.BottomStart),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmtAxisTime(points.first().ts, range), style = MaterialTheme.typography.labelSmall, color = labelColor)
            Text(fmtAxisTime(points.last().ts, range), style = MaterialTheme.typography.labelSmall, color = labelColor)
        }
    }
}

@Composable
private fun RangeStats(points: List<PricePoint>) {
    val first = points.first().close
    val last = points.last().close
    val changePct = if (first != 0.0) (last - first) / first * 100.0 else 0.0
    val minV = points.minOf { it.close }
    val maxV = points.maxOf { it.close }

    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatItem("Variation", fmtPct(changePct), if (changePct >= 0) Gain else Loss)
            StatItem("Plus bas", fmtPrice(minV))
            StatItem("Plus haut", fmtPrice(maxV))
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

/** Axis timestamp formatting: intraday shows the time, longer ranges show the date. */
private fun fmtAxisTime(tsMillis: Long, range: HistoryRange): String {
    val pattern = if (range == HistoryRange.HOURS) "HH:mm" else "dd/MM"
    return SimpleDateFormat(pattern, Locale.FRANCE).format(Date(tsMillis))
}
