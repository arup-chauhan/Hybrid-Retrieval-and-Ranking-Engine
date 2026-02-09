#!/usr/bin/env python3
"""Crawl pages from a start URL and emit dataset records for ingestion.

Default output format is JSONL (one JSON object per line), which is usually
faster and safer than CSV for long free-form text.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import sys
import time
from collections import deque
from dataclasses import dataclass
from html.parser import HTMLParser
from typing import Iterable
from urllib.parse import urljoin, urldefrag, urlparse
from urllib.request import Request, urlopen


@dataclass
class PageDoc:
    id: str
    url: str
    depth: int
    title: str
    content: str


class LinkAndTextParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.links: list[str] = []
        self._texts: list[str] = []
        self._title_parts: list[str] = []
        self._inside_title = False
        self._skip_depth = 0

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        tag_l = tag.lower()
        if tag_l in {"script", "style", "noscript"}:
            self._skip_depth += 1
            return
        if tag_l == "title":
            self._inside_title = True
        if tag_l == "a":
            for key, value in attrs:
                if key.lower() == "href" and value:
                    self.links.append(value)

    def handle_endtag(self, tag: str) -> None:
        tag_l = tag.lower()
        if tag_l in {"script", "style", "noscript"} and self._skip_depth > 0:
            self._skip_depth -= 1
        if tag_l == "title":
            self._inside_title = False

    def handle_data(self, data: str) -> None:
        if self._skip_depth > 0:
            return
        text = " ".join(data.split())
        if not text:
            return
        if self._inside_title:
            self._title_parts.append(text)
        self._texts.append(text)

    @property
    def title(self) -> str:
        return " ".join(self._title_parts).strip()

    @property
    def content(self) -> str:
        return " ".join(self._texts).strip()


def normalize_url(base_url: str, href: str) -> str | None:
    joined = urljoin(base_url, href)
    clean, _ = urldefrag(joined)
    parsed = urlparse(clean)
    if parsed.scheme not in {"http", "https"}:
        return None
    return clean


def fetch_html(url: str, timeout: float, user_agent: str) -> str | None:
    req = Request(url, headers={"User-Agent": user_agent})
    with urlopen(req, timeout=timeout) as resp:  # nosec B310
        ctype = resp.headers.get("Content-Type", "")
        if "text/html" not in ctype:
            return None
        raw = resp.read()
    return raw.decode("utf-8", errors="replace")


def build_doc(url: str, depth: int, title: str, content: str) -> PageDoc:
    digest = hashlib.sha1(url.encode("utf-8")).hexdigest()[:16]
    doc_id = f"web-{digest}"
    safe_title = title if title else f"Web doc {digest}"
    return PageDoc(id=doc_id, url=url, depth=depth, title=safe_title, content=content)


def crawl(
    start_url: str,
    max_depth: int,
    max_pages: int,
    timeout: float,
    user_agent: str,
    allow_external: bool,
    include_pattern: str | None,
    delay_ms: int,
) -> list[PageDoc]:
    start_host = urlparse(start_url).netloc
    include_re = re.compile(include_pattern) if include_pattern else None

    queue: deque[tuple[str, int]] = deque([(start_url, 0)])
    seen: set[str] = set()
    docs: list[PageDoc] = []

    while queue and len(docs) < max_pages:
        url, depth = queue.popleft()
        if url in seen:
            continue
        seen.add(url)

        try:
            html = fetch_html(url, timeout=timeout, user_agent=user_agent)
        except Exception as exc:  # pylint: disable=broad-except
            print(f"WARN: failed to fetch {url}: {exc}", file=sys.stderr)
            continue

        if not html:
            continue

        parser = LinkAndTextParser()
        parser.feed(html)
        content = parser.content
        if content:
            docs.append(build_doc(url, depth, parser.title, content))
            print(f"INFO: captured depth={depth} url={url}", file=sys.stderr)

        if depth >= max_depth:
            continue

        for href in parser.links:
            normalized = normalize_url(url, href)
            if not normalized:
                continue
            parsed = urlparse(normalized)
            if not allow_external and parsed.netloc != start_host:
                continue
            if include_re and not include_re.search(normalized):
                continue
            if normalized not in seen:
                queue.append((normalized, depth + 1))

        if delay_ms > 0:
            time.sleep(delay_ms / 1000.0)

    return docs


def write_jsonl(docs: Iterable[PageDoc], output_path: str) -> None:
    with open(output_path, "w", encoding="utf-8") as f:
        for doc in docs:
            payload = {
                "id": doc.id,
                "title": doc.title,
                "content": doc.content,
                "metadata": f"source_url={doc.url};crawl_depth={doc.depth};source=web-scrape",
                "source_url": doc.url,
                "crawl_depth": doc.depth,
            }
            f.write(json.dumps(payload, ensure_ascii=False) + "\n")


def write_csv(docs: Iterable[PageDoc], output_path: str) -> None:
    with open(output_path, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(
            f,
            fieldnames=["id", "title", "content", "metadata", "source_url", "crawl_depth"],
        )
        writer.writeheader()
        for doc in docs:
            writer.writerow(
                {
                    "id": doc.id,
                    "title": doc.title,
                    "content": doc.content,
                    "metadata": f"source_url={doc.url};crawl_depth={doc.depth};source=web-scrape",
                    "source_url": doc.url,
                    "crawl_depth": doc.depth,
                }
            )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Scrape website pages into ingestion dataset.")
    parser.add_argument("--start-url", required=True, help="Initial URL to crawl.")
    parser.add_argument("--depth", type=int, default=1, help="Max crawl depth from start URL.")
    parser.add_argument("--max-pages", type=int, default=200, help="Max pages to capture.")
    parser.add_argument("--output", required=True, help="Output file path (.jsonl or .csv).")
    parser.add_argument("--format", choices=["jsonl", "csv"], default="jsonl", help="Output format.")
    parser.add_argument("--timeout-sec", type=float, default=10.0, help="HTTP request timeout.")
    parser.add_argument("--delay-ms", type=int, default=100, help="Delay between page fetches.")
    parser.add_argument("--user-agent", default="HybridEngineScraper/1.0", help="HTTP user-agent.")
    parser.add_argument(
        "--allow-external",
        action="store_true",
        help="Allow crawling external domains (default: same domain only).",
    )
    parser.add_argument(
        "--include-pattern",
        default=None,
        help="Optional regex: only queue links matching this pattern.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    output_dir = os.path.dirname(os.path.abspath(args.output))
    os.makedirs(output_dir, exist_ok=True)

    docs = crawl(
        start_url=args.start_url,
        max_depth=max(0, args.depth),
        max_pages=max(1, args.max_pages),
        timeout=max(1.0, args.timeout_sec),
        user_agent=args.user_agent,
        allow_external=args.allow_external,
        include_pattern=args.include_pattern,
        delay_ms=max(0, args.delay_ms),
    )

    if args.format == "csv":
        write_csv(docs, args.output)
    else:
        write_jsonl(docs, args.output)

    print(f"Wrote {len(docs)} documents to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
