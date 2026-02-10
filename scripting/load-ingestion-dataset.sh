#!/usr/bin/env sh
set -eu

MODE="${MODE:-host}" # host | docker
INGEST_URL="${INGEST_URL:-}"
TOTAL_DOCS="${TOTAL_DOCS:-500000}"
START_ID="${START_ID:-1}"
CONCURRENCY="${CONCURRENCY:-40}"
CONNECT_TIMEOUT_SEC="${CONNECT_TIMEOUT_SEC:-5}"
MAX_TIME_SEC="${MAX_TIME_SEC:-20}"
OUT_DIR="${OUT_DIR:-/tmp/hybrid-load}"
CONTENT_VARIANTS="${CONTENT_VARIANTS:-256}"
CONTENT_TEMPLATE="${CONTENT_TEMPLATE:-Hybrid retrieval sample variant %s. Category electronics. Feature-rich text for lexical and semantic indexing.}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-hybrid-retrieval-and-ranking-engine}"

if [ -z "$INGEST_URL" ]; then
  if [ "$MODE" = "docker" ]; then
    INGEST_URL="http://ingestion-service:8081/api/ingest"
  else
    INGEST_URL="http://localhost:8081/api/ingest"
  fi
fi

if [ "$MODE" = "docker" ] && [ "${INSIDE_DOCKER_INGEST:-0}" != "1" ]; then
  if ! command -v docker >/dev/null 2>&1; then
    echo "ERROR: docker is required for MODE=docker"
    exit 2
  fi

  REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
  ABS_OUT_DIR="$OUT_DIR"
  mkdir -p "$ABS_OUT_DIR"
  ABS_OUT_DIR="$(cd "$ABS_OUT_DIR" && pwd)"

  DOCKER_NETWORK="${DOCKER_NETWORK:-}"
  if [ -z "$DOCKER_NETWORK" ]; then
    PREFERRED_NETWORK="${COMPOSE_PROJECT_NAME}_default"
    if docker network ls --format '{{.Name}}' | grep -Fxq "$PREFERRED_NETWORK"; then
      DOCKER_NETWORK="$PREFERRED_NETWORK"
    else
      DOCKER_NETWORK="$(docker network ls --format '{{.Name}}' | awk '/_default$/ { print; exit }')"
    fi
  fi

  if [ -z "$DOCKER_NETWORK" ]; then
    echo "ERROR: could not detect a Docker compose network. Set DOCKER_NETWORK explicitly."
    exit 2
  fi

  echo "Running ingestion load in Docker network mode"
  echo "  DOCKER_NETWORK=$DOCKER_NETWORK"
  echo "  INGEST_URL=$INGEST_URL"

  docker run --rm \
    --network "$DOCKER_NETWORK" \
    -e INSIDE_DOCKER_INGEST=1 \
    -e MODE=host \
    -e INGEST_URL="$INGEST_URL" \
    -e TOTAL_DOCS="$TOTAL_DOCS" \
    -e START_ID="$START_ID" \
    -e CONCURRENCY="$CONCURRENCY" \
    -e CONTENT_VARIANTS="$CONTENT_VARIANTS" \
    -e CONNECT_TIMEOUT_SEC="$CONNECT_TIMEOUT_SEC" \
    -e MAX_TIME_SEC="$MAX_TIME_SEC" \
    -e OUT_DIR=/out \
    -v "$ABS_OUT_DIR:/out" \
    -v "$REPO_ROOT:/workspace:ro" \
    -w /workspace \
    curlimages/curl:8.10.1 \
    sh /workspace/scripting/load-ingestion-dataset.sh
  exit $?
fi

mkdir -p "$OUT_DIR"
RES_FILE="$OUT_DIR/ingest_status_codes.txt"
rm -f "$RES_FILE"

END_ID=$((START_ID + TOTAL_DOCS - 1))

echo "Starting ingestion load"
echo "  INGEST_URL=$INGEST_URL"
echo "  START_ID=$START_ID END_ID=$END_ID TOTAL_DOCS=$TOTAL_DOCS"
echo "  CONCURRENCY=$CONCURRENCY"
echo "  CONTENT_VARIANTS=$CONTENT_VARIANTS"

export INGEST_URL RES_FILE CONNECT_TIMEOUT_SEC MAX_TIME_SEC CONTENT_VARIANTS

generate_ids() {
  i="$START_ID"
  while [ "$i" -le "$END_ID" ]; do
    echo "$i"
    i=$((i + 1))
  done
}

generate_ids | xargs -I{} -P "$CONCURRENCY" sh -c '
id="$1"
variant=$((id % CONTENT_VARIANTS))
title="Doc $id"
  content=$(printf "$CONTENT_TEMPLATE" "$variant")
metadata="category=electronics;source=synthetic;variant=$variant"
payload=$(printf "{\"id\":\"doc-%s\",\"title\":\"%s\",\"content\":\"%s\",\"metadata\":\"%s\"}" "$id" "$title" "$content" "$metadata")
code=$(curl -sS -o /dev/null -w "%{http_code}" \
  -H "Content-Type: application/json" \
  -X POST "$INGEST_URL" \
  -d "$payload" \
  --connect-timeout "$CONNECT_TIMEOUT_SEC" --max-time "$MAX_TIME_SEC" || echo "000")
echo "$code" >> "$RES_FILE"
' _ {}

total=$(wc -l < "$RES_FILE" | tr -d ' ')
errors=$(awk '$1 !~ /^2/ { c++ } END { print c+0 }' "$RES_FILE")
success=$((total - errors))
error_pct=$(awk -v e="$errors" -v t="$total" 'BEGIN { if (t==0) print 100; else printf "%.3f", (e*100.0)/t }')

echo ""
echo "Ingestion load summary"
echo "  total_requests: $total"
echo "  success_requests: $success"
echo "  error_requests: $errors"
echo "  error_rate_pct: $error_pct"

if [ "$errors" -eq 0 ]; then
  echo "  result: PASS"
  exit 0
fi

echo "  result: FAIL"
exit 1
