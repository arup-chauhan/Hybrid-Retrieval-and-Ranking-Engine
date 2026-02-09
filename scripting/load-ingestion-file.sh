#!/usr/bin/env bash
set -euo pipefail

INPUT_PATH="${INPUT_PATH:-}"
INPUT_FORMAT="${INPUT_FORMAT:-jsonl}"
INGEST_URL="${INGEST_URL:-http://localhost:8081/api/ingest}"
CONCURRENCY="${CONCURRENCY:-20}"
TIMEOUT_SEC="${TIMEOUT_SEC:-20}"
OUT_DIR="${OUT_DIR:-/tmp/hybrid-load-file}"
MAX_DOCS="${MAX_DOCS:-0}"

if [ -z "$INPUT_PATH" ]; then
  echo "ERROR: INPUT_PATH is required"
  echo "Example: INPUT_PATH=data/hotwheels.jsonl ./scripting/load-ingestion-file.sh"
  exit 2
fi

python3 ./scripting/load-ingestion-file.py \
  --input "$INPUT_PATH" \
  --format "$INPUT_FORMAT" \
  --ingest-url "$INGEST_URL" \
  --concurrency "$CONCURRENCY" \
  --timeout-sec "$TIMEOUT_SEC" \
  --out-dir "$OUT_DIR" \
  --max-docs "$MAX_DOCS"
