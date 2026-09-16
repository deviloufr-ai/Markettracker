package com.deviloufr.markettracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deviloufr.markettracker.data.AiSource
import com.deviloufr.markettracker.data.TrendVerdict
import com.deviloufr.markettracker.ui.theme.Gain
import com.deviloufr.markettracker.ui.theme.Loss

/** Generic state for an on-demand AI call rendered in a screen. */
sealed interface AiUiState<out T> {
    data object Idle : AiUiState<Nothing>
    data object Loading : AiUiState<Nothing>
    data class Error(val message: String) : AiUiState<Nothing>
    data class Success<T>(val data: T) : AiUiState<T>
}

/** Color for a directional word ("hausse"/"haussier", "baisse"/"baissier", else neutral). */
@Composable
fun directionColor(word: String): Color {
    val w = word.lowercase()
    return when {
        w.startsWith("hauss") -> Gain
        w.startsWith("baiss") -> Loss
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun directionIcon(word: String): ImageVector {
    val w = word.lowercase()
    return when {
        w.startsWith("hauss") -> Icons.Filled.TrendingUp
        w.startsWith("baiss") -> Icons.Filled.TrendingDown
        else -> Icons.Filled.TrendingFlat
    }
}

@Composable
fun verdictColor(v: TrendVerdict): Color = when (v) {
    TrendVerdict.UP -> Gain
    TrendVerdict.DOWN -> Loss
    TrendVerdict.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun verdictIcon(v: TrendVerdict): ImageVector = when (v) {
    TrendVerdict.UP -> Icons.Filled.TrendingUp
    TrendVerdict.DOWN -> Icons.Filled.TrendingDown
    TrendVerdict.NEUTRAL -> Icons.Filled.TrendingFlat
}

/** A pill showing a directional label in its color, with a trend arrow. */
@Composable
fun DirectionPill(label: String, color: Color, icon: ImageVector) {
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(end = 0.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun VerdictPill(verdict: TrendVerdict, label: String) =
    DirectionPill(label, verdictColor(verdict), verdictIcon(verdict))

@Composable
fun DirectionPill(word: String, label: String) =
    DirectionPill(label, directionColor(word), directionIcon(word))

/** Small uppercase-ish section header used to break AI output into readable blocks. */
@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Bulleted list of short strings ("• …" per line). */
@Composable
fun BulletList(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(item, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * One asset row used by the movers and opportunities lists: symbol + % (colored) + name/note,
 * with a "Suivi" tag when tracked or an "Ajouter" action to add it to the watchlist.
 */
@Composable
fun AiAssetRow(
    symbol: String,
    name: String,
    pct: Double?,
    note: String,
    tracked: Boolean,
    onAdd: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(symbol, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                pct?.let {
                    Text(
                        fmtPct(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it >= 0) Gain else Loss,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            val sub = listOfNotNull(name.ifBlank { null }, note.ifBlank { null }).joinToString(" — ")
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (tracked) {
            Text("Suivi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        } else {
            TextButton(onClick = onAdd, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Ajouter", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Color for a downside-risk severity level ("élevé" | "modéré" | "faible"). */
@Composable
fun severityColor(severity: String): Color {
    val s = severity.lowercase()
    return when {
        s.startsWith("élev") || s.startsWith("elev") || s.startsWith("fort") -> Loss
        s.startsWith("mod") -> Color(0xFFF59E0B) // amber — a middling risk
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * One row of the downside-risk radar: a tracked asset with a high risk of decline. Shows the
 * symbol, the estimated downside % (red), the reason, and a colored severity pill.
 */
@Composable
fun RiskWarningRow(
    symbol: String,
    name: String,
    downsidePct: Double?,
    severity: String,
    rationale: String
) {
    val color = severityColor(severity)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(symbol, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                downsidePct?.let {
                    Text(
                        fmtPct(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = Loss,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            val sub = listOfNotNull(name.ifBlank { null }, rationale.ifBlank { null }).joinToString(" — ")
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
            Text(
                "Risque ${severity.ifBlank { "élevé" }}",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

/** Tappable list of web sources Claude cited; each opens in the browser. */
@Composable
fun AiSources(sources: List<AiSource>) {
    if (sources.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Sources",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        sources.forEach { src ->
            TextButton(
                onClick = { runCatching { uriHandler.openUri(src.url) } },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 2.dp, horizontal = 0.dp)
            ) {
                Icon(
                    Icons.Filled.Link,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(src.title, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }
}

/** Standard "not financial advice" footnote shown on every AI result. */
@Composable
fun AiDisclaimer() {
    Text(
        "Analyse générée par IA à titre informatif — ce n'est pas un conseil financier.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Inline error with a retry action. */
@Composable
fun AiErrorRow(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Loss)
        TextButton(onClick = onRetry, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("Réessayer")
        }
    }
}
