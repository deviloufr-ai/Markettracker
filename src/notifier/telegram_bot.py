"""Notification sinks.

``TelegramNotifier`` pushes to a Telegram chat via the Bot API. If no token/chat
id is configured, ``build_notifier`` falls back to ``ConsoleNotifier`` so the app
still runs (and logs alerts) without any Telegram setup.
"""
from __future__ import annotations

import logging

import httpx

log = logging.getLogger(__name__)


class ConsoleNotifier:
    """Fallback sink: just logs the alert. Used when Telegram is not configured."""

    async def send(self, text: str) -> bool:
        log.info("ALERT (console)\n%s", text)
        return True


class TelegramNotifier:
    URL = "https://api.telegram.org/bot{token}/sendMessage"

    def __init__(self, client: httpx.AsyncClient, token: str, chat_id: str):
        self._client = client
        self._token = token
        self._chat_id = chat_id

    async def send(self, text: str) -> bool:
        try:
            resp = await self._client.post(
                self.URL.format(token=self._token),
                json={
                    "chat_id": self._chat_id,
                    "text": text,
                    "parse_mode": "HTML",
                    "disable_web_page_preview": True,
                },
            )
            resp.raise_for_status()
        except httpx.HTTPError as exc:
            log.error("Telegram send failed: %s", exc)
            return False
        return True


def build_notifier(cfg, client: httpx.AsyncClient):
    if cfg.telegram_bot_token and cfg.telegram_chat_id:
        log.info("Notifications: Telegram (chat %s)", cfg.telegram_chat_id)
        return TelegramNotifier(client, cfg.telegram_bot_token, cfg.telegram_chat_id)
    log.warning("Telegram not configured — alerts will be logged to the console only.")
    return ConsoleNotifier()
