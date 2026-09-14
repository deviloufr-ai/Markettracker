package com.deviloufr.markettracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
