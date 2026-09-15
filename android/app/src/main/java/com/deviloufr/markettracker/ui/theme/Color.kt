package com.deviloufr.markettracker.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Brand identity (fixed, theme-independent). The app no longer uses Material You
// dynamic color so it keeps one consistent look across devices.
// ---------------------------------------------------------------------------
val BrandSky = Color(0xFF0284C7)
val BrandIndigo = Color(0xFF4F46E5)

// Semantic gains/losses — used app-wide. Single source of truth (previously in Theme.kt).
val Gain = Color(0xFF16A34A)
val Loss = Color(0xFFDC2626)

// Percent-change pill badges (light theme values).
val GainBadgeBg = Color(0xFFDCFCE7)
val GainBadgeFg = Color(0xFF15803D)
val LossBadgeBg = Color(0xFFFEE2E2)
val LossBadgeFg = Color(0xFFB91C1C)

// Per-asset-class accent tints (referenced by the Market enum).
val CryptoTint = Color(0xFFF7931A)
val ForexTint = Color(0xFF16A34A)
val FuturesTint = Color(0xFFB45309)
val IndexTint = Color(0xFF7C3AED)
val EquityTint = Color(0xFF0284C7)

// ---------------------------------------------------------------------------
// Fixed Material 3 color schemes.
// ---------------------------------------------------------------------------
val AppLightColors = lightColorScheme(
    primary = BrandSky,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = BrandIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF312E81),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEEF2F6),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Loss,
    onError = Color.White,
)

val AppDarkColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF042C43),
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF818CF8),
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = Color(0xFF312E81),
    onSecondaryContainer = Color(0xFFE0E7FF),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF151A21),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
)

// ---------------------------------------------------------------------------
// Market color tokens exposed via CompositionLocal so call sites stop inlining
// hex. The dark factory swaps the pale badge fills (invisible on dark surfaces)
// for translucent gain/loss tints.
// ---------------------------------------------------------------------------
data class MarketColors(
    val gain: Color,
    val loss: Color,
    val gainBadgeBg: Color,
    val gainBadgeFg: Color,
    val lossBadgeBg: Color,
    val lossBadgeFg: Color,
    val crypto: Color,
    val forex: Color,
    val futures: Color,
    val index: Color,
    val equity: Color,
)

fun lightMarketColors() = MarketColors(
    gain = Gain,
    loss = Loss,
    gainBadgeBg = GainBadgeBg,
    gainBadgeFg = GainBadgeFg,
    lossBadgeBg = LossBadgeBg,
    lossBadgeFg = LossBadgeFg,
    crypto = CryptoTint,
    forex = ForexTint,
    futures = FuturesTint,
    index = IndexTint,
    equity = EquityTint,
)

fun darkMarketColors() = MarketColors(
    gain = Gain,
    loss = Loss,
    gainBadgeBg = Gain.copy(alpha = 0.20f),
    gainBadgeFg = Color(0xFF4ADE80),
    lossBadgeBg = Loss.copy(alpha = 0.20f),
    lossBadgeFg = Color(0xFFF87171),
    crypto = CryptoTint,
    forex = ForexTint,
    futures = FuturesTint,
    index = IndexTint,
    equity = EquityTint,
)

val LocalMarketColors = staticCompositionLocalOf { lightMarketColors() }

/** Accessor for the [MarketColors] tokens for the current theme. */
object MarketTheme {
    val colors: MarketColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMarketColors.current
}
