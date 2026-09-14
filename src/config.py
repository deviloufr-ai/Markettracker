"""Configuration loading: reads .env (secrets) and config/tickers.yaml (assets)."""
from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

import yaml
from dotenv import load_dotenv

PROJECT_ROOT = Path(__file__).resolve().parent.parent


@dataclass
class TickerConfig:
    """Per-ticker monitoring settings (defaults, possibly overridden)."""

    symbol: str
    price_change_pct: float
    price_window_minutes: int
    volume_spike_factor: float
    volume_ma_periods: int
    cooldown_minutes: int


@dataclass
class AppConfig:
    provider: str
    finnhub_api_key: str | None
    telegram_bot_token: str | None
    telegram_chat_id: str | None
    enable_news: bool
    database_path: Path
    log_level: str
    poll_interval_seconds: int
    tickers: list[TickerConfig]


def _as_bool(value: str | None, default: bool = False) -> bool:
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def load_config(tickers_path: Path | None = None) -> AppConfig:
    """Load environment (.env) and ticker/threshold config (tickers.yaml)."""
    load_dotenv(PROJECT_ROOT / ".env")

    tickers_path = tickers_path or (PROJECT_ROOT / "config" / "tickers.yaml")
    with open(tickers_path, "r", encoding="utf-8") as fh:
        raw = yaml.safe_load(fh) or {}

    defaults = raw.get("defaults", {}) or {}
    d_price_pct = float(defaults.get("price_change_pct", 3.0))
    d_window = int(defaults.get("price_window_minutes", 5))
    d_vol_factor = float(defaults.get("volume_spike_factor", 3.0))
    d_vol_periods = int(defaults.get("volume_ma_periods", 20))
    d_cooldown = int(defaults.get("cooldown_minutes", 15))

    tickers: list[TickerConfig] = []
    for entry in raw.get("tickers", []) or []:
        if isinstance(entry, str):  # allow "- AAPL" shorthand
            entry = {"symbol": entry}
        symbol = str(entry["symbol"]).upper().strip()
        tickers.append(
            TickerConfig(
                symbol=symbol,
                price_change_pct=float(entry.get("price_change_pct", d_price_pct)),
                price_window_minutes=int(entry.get("price_window_minutes", d_window)),
                volume_spike_factor=float(entry.get("volume_spike_factor", d_vol_factor)),
                volume_ma_periods=int(entry.get("volume_ma_periods", d_vol_periods)),
                cooldown_minutes=int(entry.get("cooldown_minutes", d_cooldown)),
            )
        )

    db_path_raw = os.getenv("DATABASE_PATH", "market_alert.db")
    db_path = Path(db_path_raw)
    if not db_path.is_absolute():
        db_path = PROJECT_ROOT / db_path

    return AppConfig(
        provider=os.getenv("PRICE_PROVIDER", "yahoo").strip().lower(),
        finnhub_api_key=os.getenv("FINNHUB_API_KEY") or None,
        telegram_bot_token=os.getenv("TELEGRAM_BOT_TOKEN") or None,
        telegram_chat_id=os.getenv("TELEGRAM_CHAT_ID") or None,
        enable_news=_as_bool(os.getenv("ENABLE_NEWS"), default=False),
        database_path=db_path,
        log_level=os.getenv("LOG_LEVEL", "INFO").upper(),
        poll_interval_seconds=int(raw.get("poll_interval_seconds", 30)),
        tickers=tickers,
    )
