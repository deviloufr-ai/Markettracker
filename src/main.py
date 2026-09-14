"""Main entry point: polls prices, runs detection, sends alerts.

Run from the project root:

    python -m src.main                 # live loop, provider from .env
    python -m src.main --provider sim --interval 2   # fast local demo
    python -m src.main --once          # single pass then exit
    python -m src.main --backtest data/sample_history.csv
"""
from __future__ import annotations

import argparse
import asyncio
import csv
import logging
from datetime import datetime, timezone

import httpx

from src.config import AppConfig, TickerConfig, load_config
from src.data_sources.price_feed import Quote, SimulatedProvider, build_provider
from src.detection.anomaly import DetectionEngine, Trigger
from src.notifier.telegram_bot import build_notifier
from src.storage.db import Database

log = logging.getLogger("market_alert")


def setup_logging(level: str) -> None:
    logging.basicConfig(
        level=getattr(logging, level, logging.INFO),
        format="%(asctime)s %(levelname)-7s %(name)s | %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )


def format_alert(trig: Trigger) -> str:
    local_dt = datetime.fromtimestamp(trig.ts, tz=timezone.utc).astimezone()
    return (
        f"🚨 <b>{trig.symbol}</b> — {trig.reason.replace('_', ' ')}\n"
        f"{trig.detail}\n"
        f"💵 price: {trig.price:.2f}\n"
        f"🕒 {local_dt:%Y-%m-%d %H:%M:%S %Z}"
    )


async def handle_quote(
    quote: Quote,
    cfg: TickerConfig,
    engine: DetectionEngine,
    db: Database,
    notifier,
) -> None:
    """Detect (before insert), respect cooldown, notify, log, then store."""
    triggers = engine.evaluate(quote, cfg)
    cooldown_seconds = cfg.cooldown_minutes * 60

    for trig in triggers:
        last = db.last_alert_ts(trig.symbol, trig.reason)
        if last is not None and (trig.ts - last) < cooldown_seconds:
            log.debug("Skipping %s/%s — within cooldown", trig.symbol, trig.reason)
            continue
        message = format_alert(trig)
        ok = await notifier.send(message)
        db.insert_alert(trig.symbol, trig.ts, trig.reason, trig.detail, trig.price)
        log.info("ALERT %s %s | %s | sent=%s", trig.symbol, trig.reason, trig.detail, ok)

    db.insert_price(quote.symbol, quote.ts, quote.price, quote.volume)


async def poll_once(cfg: AppConfig, provider, notifier, engine: DetectionEngine, db: Database) -> None:
    ticker_by_symbol = {t.symbol: t for t in cfg.tickers}
    quotes = await asyncio.gather(
        *(provider.get_quote(t.symbol) for t in cfg.tickers),
        return_exceptions=True,
    )
    for quote in quotes:
        if isinstance(quote, Exception):
            log.warning("Provider error: %s", quote)
            continue
        if quote is None:
            continue
        tcfg = ticker_by_symbol.get(quote.symbol)
        if tcfg is None:
            continue
        vol_str = f"{quote.volume:,.0f}" if quote.volume is not None else "n/a"
        log.debug("%s price=%.2f vol=%s", quote.symbol, quote.price, vol_str)
        await handle_quote(quote, tcfg, engine, db, notifier)


async def run_live(cfg: AppConfig, once: bool) -> None:
    db = Database(cfg.database_path)
    engine = DetectionEngine(db)
    log.info(
        "Monitoring %d tickers via '%s' | poll=%ss | %s",
        len(cfg.tickers), cfg.provider, cfg.poll_interval_seconds,
        ", ".join(t.symbol for t in cfg.tickers),
    )
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            provider = build_provider(cfg, client)
            notifier = build_notifier(cfg, client)
            while True:
                try:
                    await poll_once(cfg, provider, notifier, engine, db)
                except Exception:  # never let one bad tick kill the loop
                    log.exception("poll_once failed")
                if once:
                    break
                await asyncio.sleep(cfg.poll_interval_seconds)
    finally:
        db.close()


def run_backtest(cfg: AppConfig, csv_path: str) -> None:
    """Replay a CSV (symbol,ts,price,volume) through the detection engine offline."""
    db = Database(":memory:")
    engine = DetectionEngine(db)
    default_cfg = cfg.tickers[0] if cfg.tickers else TickerConfig(
        "?", 3.0, 5, 3.0, 20, 15
    )
    ticker_by_symbol = {t.symbol: t for t in cfg.tickers}
    n_rows = n_alerts = 0

    with open(csv_path, "r", encoding="utf-8", newline="") as fh:
        rows = sorted(csv.DictReader(fh), key=lambda r: float(r["ts"]))

    for row in rows:
        symbol = row["symbol"].upper()
        vol = row.get("volume")
        quote = Quote(
            symbol=symbol,
            price=float(row["price"]),
            volume=float(vol) if vol not in (None, "", "null") else None,
            ts=float(row["ts"]),
        )
        tcfg = ticker_by_symbol.get(symbol, default_cfg)
        for trig in engine.evaluate(quote, tcfg):
            n_alerts += 1
            stamp = datetime.fromtimestamp(trig.ts, tz=timezone.utc).strftime("%H:%M:%S")
            print(f"[{stamp}] {trig.symbol:6} {trig.reason:12} {trig.detail}")
        db.insert_price(quote.symbol, quote.ts, quote.price, quote.volume)
        n_rows += 1

    print(f"\nBacktest done: {n_rows} rows, {n_alerts} alert(s).")
    db.close()


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Market alert monitor")
    p.add_argument("--once", action="store_true", help="run a single poll pass, then exit")
    p.add_argument("--provider", choices=["yahoo", "finnhub", "sim"], help="override PRICE_PROVIDER")
    p.add_argument("--interval", type=int, help="override poll interval (seconds)")
    p.add_argument("--backtest", metavar="CSV", help="replay a CSV file offline and exit")
    return p.parse_args()


def main() -> None:
    args = parse_args()
    cfg = load_config()
    if args.provider:
        cfg.provider = args.provider
    if args.interval:
        cfg.poll_interval_seconds = args.interval
    setup_logging(cfg.log_level)

    if args.backtest:
        run_backtest(cfg, args.backtest)
        return

    try:
        asyncio.run(run_live(cfg, once=args.once))
    except KeyboardInterrupt:
        log.info("Stopped by user.")


if __name__ == "__main__":
    main()
