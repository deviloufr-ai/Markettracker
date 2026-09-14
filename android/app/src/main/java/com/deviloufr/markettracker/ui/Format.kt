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
    if (diff < 0) return "just now"
    val sec = diff / 1000
    return when {
        sec < 10 -> "just now"
        sec < 60 -> "${sec}s ago"
        sec < 3600 -> "${sec / 60}m ago"
        sec < 86400 -> "${sec / 3600}h ago"
        else -> "${sec / 86400}d ago"
    }
}
