"""Optional RSS news feed (V2, disabled by default via ENABLE_NEWS).

Polls a few financial RSS feeds and flags entries whose title/summary mentions
one of the monitored symbols. Kept intentionally simple; sentiment/NLP is future
work. ``feedparser`` is synchronous, so fetches run in a thread executor.
"""
from __future__ import annotations

import asyncio
import logging
import time
from dataclasses import dataclass

try:
    import feedparser
except ImportError:  # feedparser is only needed when ENABLE_NEWS=true
    feedparser = None

log = logging.getLogger(__name__)

DEFAULT_FEEDS = [
    "https://feeds.finance.yahoo.com/rss/2.0/headline?s=%s&region=US&lang=en-US",
]


@dataclass
class NewsItem:
    symbol: str
    title: str
    link: str
    published_ts: float


class NewsFeed:
    def __init__(self, symbols: list[str], feeds: list[str] | None = None):
        self._symbols = symbols
        self._feeds = feeds or DEFAULT_FEEDS
        self._seen: set[str] = set()

    async def poll(self) -> list[NewsItem]:
        if feedparser is None:
            log.error("ENABLE_NEWS=true but 'feedparser' is not installed.")
            return []
        items: list[NewsItem] = []
        for symbol in self._symbols:
            for template in self._feeds:
                url = template % symbol if "%s" in template else template
                parsed = await asyncio.to_thread(feedparser.parse, url)
                for entry in parsed.entries:
                    uid = entry.get("id") or entry.get("link") or entry.get("title", "")
                    if not uid or uid in self._seen:
                        continue
                    self._seen.add(uid)
                    items.append(
                        NewsItem(
                            symbol=symbol,
                            title=entry.get("title", "(no title)"),
                            link=entry.get("link", ""),
                            published_ts=time.time(),
                        )
                    )
        return items
