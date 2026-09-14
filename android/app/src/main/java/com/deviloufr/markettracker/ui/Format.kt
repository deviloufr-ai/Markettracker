package com.deviloufr.markettracker.ui

import java.util.Locale

fun fmtPrice(value: Double): String = String.format(Locale.US, "%,.2f", value)

fun fmtPct(value: Double): String = String.format(Locale.US, "%+.2f%%", value)

fun fmtVolume(value: Double): String = when {
    value >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", value / 1_000_000_000)
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000)
    value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000)
    else -> String.format(Locale.US, "%.0f", value)
}

fun timeAgo(tsMillis: Long): String {
    val diff = System.currentTimeMillis() - tsMillis
    if (diff < 0) return "à l'instant"
    val sec = diff / 1000
    return when {
        sec < 10 -> "à l'instant"
        sec < 60 -> "il y a ${sec}s"
        sec < 3600 -> "il y a ${sec / 60}min"
        sec < 86400 -> "il y a ${sec / 3600}h"
        else -> "il y a ${sec / 86400}j"
    }
}
