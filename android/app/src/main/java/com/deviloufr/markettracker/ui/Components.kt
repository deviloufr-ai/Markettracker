package com.deviloufr.markettracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.deviloufr.markettracker.data.PricePoint
import com.deviloufr.markettracker.ui.theme.BrandIndigo
import com.deviloufr.markettracker.ui.theme.BrandSky
import com.deviloufr.markettracker.ui.theme.MarketTheme

@Composable
fun EmptyState(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Compact price sparkline — the polyline core of the detail-screen chart without
 * gridlines, axes, or labels. Colored green/red by [positive] (defaults to
 * whether the series closed up), with a faint gradient fill under the line.
 * The caller sizes it via [modifier] (e.g. `Modifier.size(52.dp, 26.dp)`).
 */
@Composable
fun Sparkline(
    points: List<PricePoint>,
    modifier: Modifier = Modifier,
    positive: Boolean = (points.lastOrNull()?.close ?: 0.0) >= (points.firstOrNull()?.close ?: 0.0)
) {
    if (points.size < 2) {
        Spacer(modifier)
        return
    }
    val color = if (positive) MarketTheme.colors.gain else MarketTheme.colors.loss
    val minV = points.minOf { it.close }
    val maxV = points.maxOf { it.close }
    Canvas(modifier) {
        val n = points.size
        val span = (maxV - minV).takeIf { it > 0.0 } ?: 1.0
        val w = size.width
        val h = size.height

        fun xAt(i: Int): Float = if (n <= 1) w / 2f else w * i / (n - 1)
        fun yAt(v: Double): Float = (((maxV - v) / span) * h).toFloat()

        val line = Path()
        points.forEachIndexed { i, p ->
            val x = xAt(i)
            val y = yAt(p.close)
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(xAt(n - 1), h)
            lineTo(xAt(0), h)
            close()
        }
        drawPath(fill, brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent)))
        drawPath(
            line,
            color = color,
            style = Stroke(width = 2f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/** Solid tinted pill showing a signed percentage in gain/loss colors. */
@Composable
fun PctBadge(pct: Double, modifier: Modifier = Modifier) {
    val c = MarketTheme.colors
    val bg = if (pct >= 0) c.gainBadgeBg else c.lossBadgeBg
    val fg = if (pct >= 0) c.gainBadgeFg else c.lossBadgeFg
    Surface(color = bg, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            fmtPct(pct),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/**
 * A brand-gradient hero surface used at the top of the home screen (and other
 * hero spots). The gradient runs corner-to-corner so it honors any measured
 * size — a plain `Brush.linearGradient(colors)` would be a fixed vertical fill.
 * Content is laid out in a padded [Column]; put white/onPrimary content inside.
 */
@Composable
fun GradientHeroCard(
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(BrandSky, BrandIndigo),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .drawWithCache {
                val brush = Brush.linearGradient(
                    colors,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
                onDrawBehind { drawRect(brush) }
            }
            .padding(16.dp),
        content = content
    )
}
