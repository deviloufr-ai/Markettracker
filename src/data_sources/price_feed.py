"""Price data providers.

All providers expose the same async ``get_quote(symbol) -> Quote | None`` API,
so the rest of the app is agnostic to the source. Returning ``None`` (instead of
raising) means "no data this tick" — one failing ticker never breaks the loop.

Providers
---------
- ``yahoo``   : unofficial Yahoo Finance chart endpoint. No API key, and it
                includes a per-minute volume, which is what makes the volume
                rule usable in V1.
- ``finnhub`` : official /quote endpoint. Needs an API key. The free tier does
                NOT return volume, so only the price rule fires.
- ``sim``     : synthetic random-walk generator with injected anomalies, for
                testing detection/notifications without any network or keys.
"""
from __future__ import annotations

import logging
import random
import time
from dataclasses import dataclass

import httpx

log = logging.getLogger(__name__)


@dataclass
class Quote:
    symbol: str
    price: float
    volume: float | None
    ts: float  # epoch seconds


class YahooProvider:
    """Unofficial Yahoo Finance chart endpoint (price + 1-minute volume)."""

    URL = "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}"

    def __init__(self, client: httpx.AsyncClient):
        self._client = client

    async def get_quote(self, symbol: str) -> Quote | None:
        try:
            resp = await self._client.get(
                self.URL.format(symbol=symbol),
                params={"interval": "1m", "range": "1d"},
                headers={"User-Agent": "market-alert-app/1.0"},
            )
            resp.raise_for_status()
            result = resp.json()["chart"]["result"][0]
        except (httpx.HTTPError, KeyError, IndexError, TypeError) as exc:
            log.warning("Yahoo fetch failed for %s: %s", symbol, exc)
            return None

        meta = result.get("meta", {})
        price = meta.get("regularMarketPrice")
        if price is None:
            log.warning("Yahoo returned no price for %s", symbol)
            return None
        ts = float(meta.get("regularMarketTime") or time.time())

        volume: float | None = None
        try:
            vols = result["indicators"]["quote"][0]["volume"]
            for v in reversed(vols):  # latest non-null 1-minute bar
                if v is not None:
                    volume = float(v)
                    break
        except (KeyError, IndexError, TypeError):
            pass

        return Quote(symbol, float(price), volume, ts)


class FinnhubProvider:
    """Finnhub /quote endpoint. Free tier: price only (volume is None)."""

    URL = "https://finnhub.io/api/v1/quote"

    def __init__(self, client: httpx.AsyncClient, api_key: str):
        self._client = client
        self._api_key = api_key

    async def get_quote(self, symbol: str) -> Quote | None:
        try:
            resp = await self._client.get(
                self.URL, params={"symbol": symbol, "token": self._api_key}
            )
            resp.raise_for_status()
            data = resp.json()
        except httpx.HTTPError as exc:
            log.warning("Finnhub fetch failed for %s: %s", symbol, exc)
            return None

        price = data.get("c")  # current price
        if not price:
            return None
        ts = float(data.get("t") or time.time())
        return Quote(symbol, float(price), None, ts)


class SimulatedProvider:
    """Random-walk generator that periodically injects price/volume anomalies.

    Great for a live demo: run with a short interval and you will see alerts fire
    once enough history has accumulated. No network, no API key.
    """

    def __init__(self, seed: int | None = None):
        self._rng = random.Random(seed)
        self._state: dict[str, dict[str, float]] = {}

    async def get_quote(self, symbol: str) -> Quote | None:
        st = self._state.setdefault(
            symbol,
            {"price": self._rng.uniform(50, 500), "base_vol": self._rng.uniform(1e5, 1e6)},
        )
        # normal random walk
        st["price"] = max(1.0, st["price"] * (1 + self._rng.gauss(0, 0.003)))
        volume = st["base_vol"] * (1 + abs(self._rng.gauss(0, 0.3)))

        # ~8% of ticks: inject a sharp move + volume burst
        if self._rng.random() < 0.08:
            jump = self._rng.choice([-1, 1]) * self._rng.uniform(0.04, 0.09)
            st["price"] = max(1.0, st["price"] * (1 + jump))
            volume *= self._rng.uniform(4, 8)

        return Quote(symbol, round(st["price"], 2), round(volume), time.time())


def build_provider(cfg, client: httpx.AsyncClient):
    """Instantiate the provider named in the config."""
    provider = cfg.provider
    if provider == "finnhub":
        if not cfg.finnhub_api_key:
            raise SystemExit("PRICE_PROVIDER=finnhub but FINNHUB_API_KEY is not set.")
        return FinnhubProvider(client, cfg.finnhub_api_key)
    if provider == "sim":
        return SimulatedProvider()
    if provider == "yahoo":
        return YahooProvider(client)
    raise SystemExit(f"Unknown PRICE_PROVIDER: {provider!r} (use yahoo|finnhub|sim)")
