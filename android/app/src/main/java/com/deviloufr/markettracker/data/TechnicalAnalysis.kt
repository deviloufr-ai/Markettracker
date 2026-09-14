package com.deviloufr.markettracker.data

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * On-device, network-free technical analysis of a price-history series.
 *
 * Pure and deterministic (like [com.deviloufr.markettracker.detection.AnomalyDetector]) so it can
 * be unit-tested and reused anywhere. It turns the same [PricePoint] list the detail chart already
 * fetches into a rule-based [TechnicalSignals] read (hausse / baisse / neutre) with a confidence
 * score and short French rationale. It intentionally makes no market predictions of its own — it
 * summarises well-known indicators (SMA crossover, momentum, RSI, volatility, range position).
 */
object TechnicalAnalysis {

    /** RSI look-back period (Wilder's classic 14). */
    private const val RSI_PERIOD = 14

    /**
     * Analyse [points] (oldest first) plus the optional live [quote]. Returns a NEUTRAL
     * "données insuffisantes" result when the series is too short to say anything useful.
     */
    fun analyze(points: List<PricePoint>, quote: Quote?): TechnicalSignals {
        val closes = buildCloses(points, quote)
        if (closes.size < 5) return insufficient()

        val first = closes.first()
        val last = closes.last()
        val minV = closes.min()
        val maxV = closes.max()

        val momentumPct = if (first != 0.0) (last - first) / first * 100.0 else 0.0
        val rangePositionPct = ((last - minV) / ((maxV - minV).takeIf { it > 0.0 } ?: 1.0)) * 100.0
        val volatilityPct = returnsStdevPct(closes)
        val rsi = rsi(closes)

        // Short vs long simple moving averages, sized relative to the series length so the
        // crossover is meaningful for both intraday (many points) and monthly (few points) ranges.
        val longWin = closes.size.coerceAtMost(30).coerceAtLeast(3)
        val shortWin = (longWin / 3).coerceAtLeast(2)
        val smaShort = closes.takeLast(shortWin).average()
        val smaLong = closes.takeLast(longWin).average()
        val smaGapPct = if (smaLong != 0.0) (smaShort - smaLong) / smaLong * 100.0 else 0.0
        val trendLabel = when {
            smaGapPct > 0.3 -> "Moyenne courte au-dessus de la longue"
            smaGapPct < -0.3 -> "Moyenne courte sous la longue"
            else -> "Moyennes courte et longue confondues"
        }

        // --- scoring: each signal nudges a bullish/bearish score in [-100, 100] ---
        var score = 0.0
        val rationale = ArrayList<String>()

        // Momentum over the window.
        when {
            momentumPct > 2.0 -> { score += 28; rationale.add("Momentum positif (${signed(momentumPct)} sur la période)") }
            momentumPct < -2.0 -> { score -= 28; rationale.add("Momentum négatif (${signed(momentumPct)} sur la période)") }
            else -> rationale.add("Momentum quasi plat (${signed(momentumPct)})")
        }

        // SMA crossover.
        when {
            smaGapPct > 0.3 -> { score += 26; rationale.add(trendLabel) }
            smaGapPct < -0.3 -> { score -= 26; rationale.add(trendLabel) }
            else -> rationale.add(trendLabel)
        }

        // RSI: overbought / oversold / neutral.
        if (rsi != null) {
            when {
                rsi >= 70 -> { score -= 18; rationale.add("RSI ${fmt1(rsi)} : zone de surachat") }
                rsi <= 30 -> { score += 18; rationale.add("RSI ${fmt1(rsi)} : zone de survente") }
                rsi >= 55 -> { score += 10; rationale.add("RSI ${fmt1(rsi)} : biais acheteur") }
                rsi <= 45 -> { score -= 10; rationale.add("RSI ${fmt1(rsi)} : biais vendeur") }
                else -> rationale.add("RSI ${fmt1(rsi)} : neutre")
            }
        }

        // Position within the period's range.
        when {
            rangePositionPct >= 80 -> { score += 8; rationale.add("Cours proche du plus haut de la période") }
            rangePositionPct <= 20 -> { score -= 8; rationale.add("Cours proche du plus bas de la période") }
        }

        // High volatility tempers confidence but doesn't set direction.
        if (volatilityPct > 4.0) rationale.add("Volatilité élevée (${fmt1(volatilityPct)} %) : signal moins fiable")

        val verdict = when {
            score >= 20 -> TrendVerdict.UP
            score <= -20 -> TrendVerdict.DOWN
            else -> TrendVerdict.NEUTRAL
        }
        // Confidence: magnitude of the score, damped when volatility is high.
        val volPenalty = (volatilityPct - 3.0).coerceAtLeast(0.0) * 4.0
        val confidence = (abs(score) - volPenalty).coerceIn(0.0, 100.0).toInt()

        return TechnicalSignals(
            verdict = verdict,
            confidence = confidence,
            momentumPct = momentumPct,
            rsi = rsi,
            trendLabel = trendLabel,
            volatilityPct = volatilityPct,
            rangePositionPct = rangePositionPct,
            rationale = rationale
        )
    }

    /** Closes oldest→newest, with the live quote appended when it's newer than the last point. */
    private fun buildCloses(points: List<PricePoint>, quote: Quote?): List<Double> {
        val base = points.map { it.close }
        val q = quote?.price ?: return base
        return if (base.isEmpty() || base.last() != q) base + q else base
    }

    /** Wilder's RSI over [RSI_PERIOD]; null when there aren't enough closes. */
    private fun rsi(closes: List<Double>): Double? {
        if (closes.size <= RSI_PERIOD) return null
        var gains = 0.0
        var losses = 0.0
        for (i in 1..RSI_PERIOD) {
            val d = closes[i] - closes[i - 1]
            if (d >= 0) gains += d else losses -= d
        }
        var avgGain = gains / RSI_PERIOD
        var avgLoss = losses / RSI_PERIOD
        for (i in RSI_PERIOD + 1 until closes.size) {
            val d = closes[i] - closes[i - 1]
            val gain = if (d > 0) d else 0.0
            val loss = if (d < 0) -d else 0.0
            avgGain = (avgGain * (RSI_PERIOD - 1) + gain) / RSI_PERIOD
            avgLoss = (avgLoss * (RSI_PERIOD - 1) + loss) / RSI_PERIOD
        }
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    /** Standard deviation of successive percentage returns, expressed in %. */
    private fun returnsStdevPct(closes: List<Double>): Double {
        if (closes.size < 3) return 0.0
        val returns = ArrayList<Double>(closes.size - 1)
        for (i in 1 until closes.size) {
            val prev = closes[i - 1]
            if (prev != 0.0) returns.add((closes[i] - prev) / prev * 100.0)
        }
        if (returns.size < 2) return 0.0
        val mean = returns.average()
        val variance = returns.sumOf { (it - mean) * (it - mean) } / returns.size
        return sqrt(variance)
    }

    private fun insufficient() = TechnicalSignals(
        verdict = TrendVerdict.NEUTRAL,
        confidence = 0,
        momentumPct = 0.0,
        rsi = null,
        trendLabel = "Données insuffisantes",
        volatilityPct = 0.0,
        rangePositionPct = 50.0,
        rationale = listOf("Historique trop court pour une analyse fiable.")
    )

    private fun signed(v: Double): String = String.format(java.util.Locale.US, "%+.2f %%", v)
    private fun fmt1(v: Double): String = String.format(java.util.Locale.US, "%.1f", v)
}
