#!/usr/bin/env python3
"""Load dataset records from JSONL/CSV and ingest into ingestion-service via REST."""

from __future__ import annotations

import argparse
import csv
import json
import os
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Iterator


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Load dataset file into ingestion-service.")
    parser.add_argument("--input", required=True, help="Input dataset path (.jsonl or .csv).")
    parser.add_argument(
        "--format",
        choices=["jsonl", "csv"],
        default="jsonl",
        help="Input file format.",
    )
    parser.add_argument(
        "--ingest-url",
        default="http://localhost:8081/api/ingest",
        help="Ingestion endpoint URL.",
    )
    parser.add_argument("--concurrency", type=int, default=20, help="Parallel request workers.")
    parser.add_argument("--timeout-sec", type=float, default=20.0, help="Request timeout.")
    parser.add_argument(
        "--out-dir",
        default="/tmp/hybrid-load-file",
        help="Output directory for status artifacts.",
    )
    parser.add_argument(
        "--max-docs",
        type=int,
        default=0,
        help="Optional cap; 0 means all records.",
    )
    return parser.parse_args()


def normalize_record(raw: dict, idx: int) -> dict:
    doc_id = str(raw.get("id") or f"file-doc-{idx}")
    title = str(raw.get("title") or f"File doc {idx}")
    content = str(raw.get("content") or "")
    metadata = str(raw.get("metadata") or "source=file-load")
    return {"id": doc_id, "title": title, "content": content, "metadata": metadata}


def iter_jsonl(path: str) -> Iterator[dict]:
    with open(path, "r", encoding="utf-8") as f:
        for line_no, line in enumerate(f, start=1):
            text = line.strip()
            if not text:
                continue
            try:
                yield json.loads(text)
            except json.JSONDecodeError:
                raise ValueError(f"Invalid JSONL at line {line_no}")


def iter_csv(path: str) -> Iterator[dict]:
    with open(path, "r", encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            yield row


def post_doc(ingest_url: str, doc: dict, timeout_sec: float) -> str:
    payload = json.dumps(doc).encode("utf-8")
    request = urllib.request.Request(
        ingest_url,
        data=payload,
        method="POST",
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout_sec) as resp:  # nosec B310
            return str(resp.status)
    except urllib.error.HTTPError as exc:
        return str(exc.code)
    except Exception:
        return "000"


def main() -> int:
    args = parse_args()

    if args.format == "csv":
        source_iter = iter_csv(args.input)
    else:
        source_iter = iter_jsonl(args.input)

    docs: list[dict] = []
    for idx, raw in enumerate(source_iter, start=1):
        docs.append(normalize_record(raw, idx))
        if args.max_docs > 0 and len(docs) >= args.max_docs:
            break

    os.makedirs(args.out_dir, exist_ok=True)
    status_file = os.path.join(args.out_dir, "ingest_status_codes.txt")
    if os.path.exists(status_file):
        os.remove(status_file)

    print("Starting file ingestion load")
    print(f"  input={args.input} format={args.format}")
    print(f"  ingest_url={args.ingest_url}")
    print(f"  docs={len(docs)} concurrency={args.concurrency}")

    started = time.time()
    statuses: list[str] = []

    with ThreadPoolExecutor(max_workers=max(1, args.concurrency)) as pool:
        futures = [pool.submit(post_doc, args.ingest_url, doc, args.timeout_sec) for doc in docs]
        for future in as_completed(futures):
            statuses.append(future.result())

    with open(status_file, "w", encoding="utf-8") as f:
        for code in statuses:
            f.write(code + "\n")

    total = len(statuses)
    errors = sum(1 for s in statuses if not s.startswith("2"))
    success = total - errors
    error_pct = 100.0 if total == 0 else (errors * 100.0) / total
    elapsed = time.time() - started

    print("\nIngestion load summary")
    print(f"  total_requests: {total}")
    print(f"  success_requests: {success}")
    print(f"  error_requests: {errors}")
    print(f"  error_rate_pct: {error_pct:.3f}")
    print(f"  duration_sec: {elapsed:.2f}")
    print(f"  status_file: {status_file}")

    if errors == 0:
        print("  result: PASS")
        return 0

    print("  result: FAIL")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
