# Market Alert App

Monitors a configurable list of tickers, detects significant **price** and
**volume** moves, and pushes a near-instant **Telegram** notification so you can
decide quickly. **V1 = surveillance + alerting only — no automated order
placement** (see [Roadmap](#roadmap)).

Target latency is a few seconds to a few minutes (polling based), not
microsecond co-location.

---

## Features

- Pluggable price feed: **Yahoo** (no key, includes volume), **Finnhub** (key,
  price only on free tier), or a built-in **simulator** for offline testing.
- Anomaly detection with per-ticker thresholds:
  - price change ≥ X % over a sliding window (default ±3 % / 5 min);
  - volume ≥ N × its moving average (default 3× over 20 samples).
- **Telegram** notifications, with automatic fallback to console logging when
  Telegram isn't configured.
- **SQLite** history of every price sample and every alert (for tuning
  thresholds after the fact).
- Per-ticker/-reason **cooldown** to avoid alert spam.
- **Backtest mode** to replay historical/synthetic CSV data before going live.

## Project layout

```
market-alert-app/
├── config/tickers.yaml         # assets to watch + thresholds
├── data/sample_history.csv     # demo data for --backtest
├── src/
│   ├── config.py               # loads .env + tickers.yaml
│   ├── data_sources/
│   │   ├── price_feed.py        # Yahoo / Finnhub / Simulated providers
│   │   └── news_feed.py         # optional RSS news (V2)
│   ├── detection/anomaly.py     # price / volume rules (pure, unit-tested)
│   ├── notifier/telegram_bot.py # Telegram + console notifier
│   ├── storage/db.py            # SQLite history + alert log
│   └── main.py                  # polling loop / orchestration
├── tests/test_anomaly.py
├── .env.example
└── requirements.txt
```

## Setup

```bash
# from the project root
python -m venv .venv
.venv\Scripts\activate            # Windows (PowerShell)
# source .venv/bin/activate       # macOS / Linux
pip install -r requirements.txt

copy .env.example .env             # Windows  (cp on macOS/Linux)
# then edit .env
```

Requires Python 3.11+.

## Telegram setup

1. In Telegram, message **@BotFather**, send `/newbot`, follow the prompts. It
   gives you a **bot token** (`123456:ABC-...`). Put it in `TELEGRAM_BOT_TOKEN`.
2. Send any message to your new bot (so it's allowed to message you back).
3. Get your **chat id**: open
   `https://api.telegram.org/bot<YOUR_TOKEN>/getUpdates` in a browser and read
   `result[].message.chat.id`. Put it in `TELEGRAM_CHAT_ID`.

If either value is empty, alerts are just logged to the console — handy while
you tune thresholds.

## Run

```bash
# live monitoring (provider taken from .env)
python -m src.main

# fast local demo, no keys/network — you'll see alerts after it warms up
python -m src.main --provider sim --interval 2

# single poll pass then exit (useful for cron / debugging)
python -m src.main --once

# replay historical/synthetic data offline
python -m src.main --backtest data/sample_history.csv
```

Run everything from the **project root** so `python -m src.main` resolves the
`src` package.

## Tests

```bash
python -m pytest            # or: python tests/test_anomaly.py
```

## Detection logic (V1)

- **Price move** — compares the latest price to the oldest sample still inside
  the window (`price_window_minutes`). Fires when `|Δ%| ≥ price_change_pct`.
- **Volume spike** — compares the latest volume to the moving average of the
  last `volume_ma_periods` samples. Fires when `volume ≥ volume_spike_factor ×
  average`. The rule only arms once enough samples exist (a warm-up period).

Thresholds live in `config/tickers.yaml`, globally under `defaults:` and
overridable per ticker.

## Notes & limitations

- **Volume data.** The `yahoo` provider returns a per-minute volume bar, which
  works for the volume rule. **Finnhub's free `/quote` has no volume**, so with
  `PRICE_PROVIDER=finnhub` only the price rule fires. If you poll faster than one
  minute, successive reads can land in the same 1-minute bar — keep the poll
  interval at ~30–60 s or coarser for the volume rule to behave.
- The Yahoo endpoint is **unofficial**; it can rate-limit or change. For
  production, consider a paid provider (Polygon.io, Finnhub paid, a broker feed).
- **API keys** are read from `.env` only — never hard-code them.
- Every alert is written to the `alerts` table with a timestamp, ticker and
  reason, so you can review whether your thresholds are well calibrated.

## Roadmap

- **V2**: news/sentiment signal (an RSS scaffold exists in
  `src/data_sources/news_feed.py`, gated behind `ENABLE_NEWS`), WebSocket feeds
  for lower latency.
- **Later (separate design)**: order execution via a broker API (Alpaca,
  Interactive Brokers). This is intentionally **out of scope for V1** and must
  come with its own risk-management design (position sizing, stop-loss, kill
  switch). Do not bolt trading onto the alerting loop without that.
