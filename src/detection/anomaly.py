"""Anomaly detection rules.

The individual ``check_*`` functions are deliberately pure (no DB, no I/O) so
they are trivial to unit-test on synthetic data. ``DetectionEngine`` wires them
to the SQLite history.
"""
from __future__ import annotations

from dataclasses import dataclass
from statistics import mean


@dataclass
class Trigger:
    symbol: str
    reason: str      # 'price_move' | 'volume_spike'
    detail: str      # human-readable explanation for the notification
    price: float
    ts: float


def _baseline_price(history: list[tuple[float, float]], window_start_ts: float) -> float | None:
    """Price of the oldest observation still inside the window [window_start, now]."""
    for ts, price in history:  # history is ascending by ts
        if ts >= window_start_ts:
            return price
    return None


def check_price_move(
    symbol: str,
    price: float,
    ts: float,
    history: list[tuple[float, float]],
    threshold_pct: float,
    window_seconds: float,
) -> Trigger | None:
    """Trigger if the price moved >= threshold_pct vs the start of the window."""
    baseline = _baseline_price(history, ts - window_seconds)
    if not baseline:  # None or 0.0 -> not enough data / invalid
        return None
    change = (price - baseline) / baseline * 100.0
    if abs(change) >= threshold_pct:
        arrow = "🔺" if change > 0 else "🔻"
        detail = (
            f"{arrow} {change:+.2f}% over ~{int(window_seconds / 60)}m "
            f"({baseline:.2f} → {price:.2f})"
        )
        return Trigger(symbol, "price_move", detail, price, ts)
    return None


def check_volume_spike(
    symbol: str,
    volume: float | None,
    ts: float,
    prior_volumes: list[float],
    factor: float,
    min_samples: int,
    price: float,
) -> Trigger | None:
    """Trigger if current volume >= factor * moving-average of prior volumes."""
    if volume is None:
        return None
    vols = [v for v in prior_volumes if v is not None]
    if len(vols) < min_samples:
        return None
    avg = mean(vols)
    if avg <= 0:
        return None
    ratio = volume / avg
    if ratio >= factor:
        detail = (
            f"📊 volume {ratio:.1f}× the {len(vols)}-sample average "
            f"({volume:,.0f} vs avg {avg:,.0f})"
        )
        return Trigger(symbol, "volume_spike", detail, price, ts)
    return None


class DetectionEngine:
    """Evaluates the rules for one observation against the stored history."""

    def __init__(self, db):
        self.db = db

    def evaluate(self, quote, cfg) -> list[Trigger]:
        """``quote`` needs .symbol/.price/.volume/.ts; ``cfg`` is a TickerConfig.

        Must be called *before* the current observation is inserted, so the
        history it reads excludes the current tick.
        """
        triggers: list[Trigger] = []
        window_seconds = cfg.price_window_minutes * 60

        history = self.db.recent_prices(quote.symbol, since_ts=quote.ts - window_seconds - 1)
        price_trigger = check_price_move(
            quote.symbol, quote.price, quote.ts, history,
            cfg.price_change_pct, window_seconds,
        )
        if price_trigger:
            triggers.append(price_trigger)

        prior_volumes = self.db.last_volumes(
            quote.symbol, cfg.volume_ma_periods, before_ts=quote.ts
        )
        volume_trigger = check_volume_spike(
            quote.symbol, quote.volume, quote.ts, prior_volumes,
            cfg.volume_spike_factor, cfg.volume_ma_periods, quote.price,
        )
        if volume_trigger:
            triggers.append(volume_trigger)

        return triggers
