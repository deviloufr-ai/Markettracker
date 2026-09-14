"""SQLite storage: price/volume history + a log of every alert sent.

Uses the stdlib ``sqlite3`` (no extra dependency). A single connection guarded
by a lock is plenty for the polling workload of a handful of tickers.
"""
from __future__ import annotations

import sqlite3
import threading
from pathlib import Path

_SCHEMA = """
CREATE TABLE IF NOT EXISTS prices (
    id     INTEGER PRIMARY KEY AUTOINCREMENT,
    symbol TEXT    NOT NULL,
    ts     REAL    NOT NULL,          -- epoch seconds
    price  REAL    NOT NULL,
    volume REAL                       -- may be NULL (e.g. Finnhub free tier)
);
CREATE INDEX IF NOT EXISTS idx_prices_symbol_ts ON prices(symbol, ts);

CREATE TABLE IF NOT EXISTS alerts (
    id     INTEGER PRIMARY KEY AUTOINCREMENT,
    symbol TEXT    NOT NULL,
    ts     REAL    NOT NULL,
    reason TEXT    NOT NULL,          -- 'price_move' | 'volume_spike' | 'news'
    detail TEXT,
    price  REAL
);
CREATE INDEX IF NOT EXISTS idx_alerts_symbol_reason ON alerts(symbol, reason);
"""


class Database:
    def __init__(self, path: str | Path = ":memory:"):
        self._conn = sqlite3.connect(str(path), check_same_thread=False)
        self._conn.row_factory = sqlite3.Row
        self._lock = threading.Lock()
        with self._lock:
            self._conn.executescript(_SCHEMA)
            self._conn.commit()

    # -- writes ---------------------------------------------------------------

    def insert_price(self, symbol: str, ts: float, price: float, volume: float | None) -> None:
        with self._lock:
            self._conn.execute(
                "INSERT INTO prices(symbol, ts, price, volume) VALUES (?, ?, ?, ?)",
                (symbol, ts, price, volume),
            )
            self._conn.commit()

    def insert_alert(self, symbol: str, ts: float, reason: str, detail: str, price: float) -> None:
        with self._lock:
            self._conn.execute(
                "INSERT INTO alerts(symbol, ts, reason, detail, price) VALUES (?, ?, ?, ?, ?)",
                (symbol, ts, reason, detail, price),
            )
            self._conn.commit()

    # -- reads ----------------------------------------------------------------

    def recent_prices(self, symbol: str, since_ts: float) -> list[tuple[float, float]]:
        """(ts, price) rows for ``symbol`` with ts >= since_ts, oldest first."""
        with self._lock:
            cur = self._conn.execute(
                "SELECT ts, price FROM prices WHERE symbol = ? AND ts >= ? ORDER BY ts ASC",
                (symbol, since_ts),
            )
            return [(row["ts"], row["price"]) for row in cur.fetchall()]

    def last_volumes(self, symbol: str, limit: int, before_ts: float) -> list[float]:
        """Up to ``limit`` most-recent non-null volumes strictly before ``before_ts``
        (returned oldest-first)."""
        with self._lock:
            cur = self._conn.execute(
                "SELECT volume FROM prices "
                "WHERE symbol = ? AND ts < ? AND volume IS NOT NULL "
                "ORDER BY ts DESC LIMIT ?",
                (symbol, before_ts, limit),
            )
            rows = [row["volume"] for row in cur.fetchall()]
        rows.reverse()
        return rows

    def last_alert_ts(self, symbol: str, reason: str) -> float | None:
        with self._lock:
            cur = self._conn.execute(
                "SELECT MAX(ts) AS last FROM alerts WHERE symbol = ? AND reason = ?",
                (symbol, reason),
            )
            row = cur.fetchone()
        return row["last"] if row and row["last"] is not None else None

    def close(self) -> None:
        with self._lock:
            self._conn.close()
