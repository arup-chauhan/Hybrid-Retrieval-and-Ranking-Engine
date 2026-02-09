#!/usr/bin/env bash
set -euo pipefail

TARGET_DOCS="${TARGET_DOCS:-500000}"
CONCURRENCY="${CONCURRENCY:-40}"
BENCH_REQUESTS="${BENCH_REQUESTS:-100}"
BENCH_CONCURRENCY="${BENCH_CONCURRENCY:-20}"
CONTENT_VARIANTS="${CONTENT_VARIANTS:-256}"
FAST_VALIDATION_MODE="${FAST_VALIDATION_MODE:-true}"
SOLR_BATCH_SIZE="${SOLR_BATCH_SIZE:-1000}"
SOLR_COMMIT_INTERVAL_MS="${SOLR_COMMIT_INTERVAL_MS:-1000}"
POLL_INTERVAL_SEC="${POLL_INTERVAL_SEC:-20}"
MAX_WAIT_SEC="${MAX_WAIT_SEC:-10800}" # 3 hours

RUN_ID="$(date +%Y-%m-%d-%H%M%S)"
REPORT_DIR="docs/benchmarks/500k-${RUN_ID}"
mkdir -p "$REPORT_DIR"
RUN_TOPIC="${RUN_TOPIC:-ingestion-topic-${RUN_ID}}"
RUN_GROUP_ID="${RUN_GROUP_ID:-indexing-group-${RUN_ID}}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-hybrid-retrieval-and-ranking-engine}"

LOAD_OUT_DIR="${REPORT_DIR}/load"
COLD_OUT_DIR="${REPORT_DIR}/cold"
WARM_OUT_DIR="${REPORT_DIR}/warm"

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
  echo "ERROR: could not detect Docker compose network. Set DOCKER_NETWORK explicitly."
  exit 2
fi

wait_for_http() {
  local url="$1"
  local attempts="${2:-60}"
  local sleep_sec="${3:-2}"
  local expected_code="${4:-any}"
  local i code
  for i in $(seq 1 "$attempts"); do
    code="$(curl --max-time 5 -sS -o /dev/null -w '%{http_code}' "$url" || echo 000)"
    if [ "$expected_code" = "any" ] && [ "$code" != "000" ]; then
      return 0
    fi
    if [ "$expected_code" != "any" ] && [ "$code" = "$expected_code" ]; then
      return 0
    fi
    sleep "$sleep_sec"
  done
  return 1
}

wait_for_ingestion_ready() {
  local attempts="${1:-60}"
  local sleep_sec="${2:-2}"
  local i code
  for i in $(seq 1 "$attempts"); do
    code="$(curl --max-time 10 -sS -o /dev/null -w '%{http_code}' \
      -H 'Content-Type: application/json' \
      -X POST 'http://localhost:8081/api/ingest' \
      -d '{"id":"readiness-probe-doc","title":"probe","content":"probe","metadata":"probe"}' || echo 000)"
    if [ "$code" = "200" ]; then
      return 0
    fi
    sleep "$sleep_sec"
  done
  return 1
}

echo "500K validation run started"
echo "  run_id=$RUN_ID"
echo "  report_dir=$REPORT_DIR"
echo "  docker_network=$DOCKER_NETWORK"
echo "  fast_validation_mode=$FAST_VALIDATION_MODE"
echo "  solr_batch_size=$SOLR_BATCH_SIZE solr_commit_interval_ms=$SOLR_COMMIT_INTERVAL_MS"
echo "  run_topic=$RUN_TOPIC run_group_id=$RUN_GROUP_ID"

echo ""
echo "[1/5] Prepare indexing-service scaling mode"
APP_KAFKA_TOPIC="$RUN_TOPIC" \
INDEXING_CONSUMER_GROUP_ID="$RUN_GROUP_ID" \
VECTOR_EMBEDDING_FAST_VALIDATION_MODE="$FAST_VALIDATION_MODE" \
SOLR_BATCH_SIZE="$SOLR_BATCH_SIZE" \
SOLR_COMMIT_INTERVAL_MS="$SOLR_COMMIT_INTERVAL_MS" \
docker-compose -p "$COMPOSE_PROJECT_NAME" up -d --build ingestion-service indexing-service >/dev/null

echo "  waiting for ingestion-service health endpoint..."
if ! wait_for_http "http://localhost:8081/api/ingest" 90 2 any; then
  echo "ERROR: ingestion-service is not reachable after restart."
  exit 1
fi
echo "  waiting for ingestion-service publish path..."
if ! wait_for_ingestion_ready 90 2; then
  echo "ERROR: ingestion-service publish path is not ready."
  exit 1
fi

echo ""
echo "[2/5] Load synthetic documents through ingestion-service"
baseline_solr_json="$(docker run --rm --network "$DOCKER_NETWORK" curlimages/curl:8.10.1 -sS \
  'http://solr:8983/solr/hybrid_collection/select?q=*:*&rows=0&wt=json')"
baseline_solr_count="$(echo "$baseline_solr_json" | sed -n 's/.*"numFound":[[:space:]]*\([0-9][0-9]*\).*/\1/p' | head -n1)"
baseline_solr_count="${baseline_solr_count:-0}"
baseline_vector_count="$(docker-compose -p "$COMPOSE_PROJECT_NAME" exec -T postgres psql -U hybrid -d metadata_db -t -A -c 'SELECT COUNT(*) FROM vector_metadata;' | tr -d '[:space:]')"
baseline_vector_count="${baseline_vector_count:-0}"
START_ID="${START_ID:-$((baseline_vector_count + 1))}"
target_solr_count=$((baseline_solr_count + TARGET_DOCS))
target_vector_count=$((baseline_vector_count + TARGET_DOCS))
echo "  baseline: solr=$baseline_solr_count vector_metadata=$baseline_vector_count"
echo "  target:   solr=$target_solr_count vector_metadata=$target_vector_count"
echo "  start_id: $START_ID"

MODE=host TOTAL_DOCS="$TARGET_DOCS" START_ID="$START_ID" CONCURRENCY="$CONCURRENCY" CONTENT_VARIANTS="$CONTENT_VARIANTS" OUT_DIR="$LOAD_OUT_DIR" ./scripting/load-ingestion-dataset.sh

echo ""
echo "[3/5] Wait for indexing completion (Solr + vector_metadata)"

deadline=$(( $(date +%s) + MAX_WAIT_SEC ))
solr_count=0
vector_count=0

while true; do
  if [ "$(date +%s)" -gt "$deadline" ]; then
    echo "ERROR: timeout waiting for index counts to reach target."
    echo "  solr_count=$solr_count vector_count=$vector_count target=$TARGET_DOCS"
    exit 1
  fi

  solr_json="$(docker run --rm --network "$DOCKER_NETWORK" curlimages/curl:8.10.1 -sS \
    'http://solr:8983/solr/hybrid_collection/select?q=*:*&rows=0&wt=json')"
  solr_count="$(echo "$solr_json" | sed -n 's/.*"numFound":[[:space:]]*\([0-9][0-9]*\).*/\1/p' | head -n1)"
  solr_count="${solr_count:-0}"

  vector_count="$(docker-compose -p "$COMPOSE_PROJECT_NAME" exec -T postgres psql -U hybrid -d metadata_db -t -A -c 'SELECT COUNT(*) FROM vector_metadata;' | tr -d '[:space:]')"
  vector_count="${vector_count:-0}"

  echo "  progress: solr=$solr_count/$target_solr_count vector_metadata=$vector_count/$target_vector_count"

  if [ "$solr_count" -ge "$target_solr_count" ] && [ "$vector_count" -ge "$target_vector_count" ]; then
    break
  fi
  sleep "$POLL_INTERVAL_SEC"
done

echo ""
echo "[4/5] Run cold/warm benchmark passes"
MODE=docker DOCKER_NETWORK="$DOCKER_NETWORK" REQUESTS="$BENCH_REQUESTS" CONCURRENCY="$BENCH_CONCURRENCY" OUT_DIR="$COLD_OUT_DIR" ./scripting/benchmark-hybrid.sh || true
MODE=docker DOCKER_NETWORK="$DOCKER_NETWORK" REQUESTS="$BENCH_REQUESTS" CONCURRENCY="$BENCH_CONCURRENCY" OUT_DIR="$WARM_OUT_DIR" ./scripting/benchmark-hybrid.sh || true

echo ""
echo "[5/5] Write report"

cat > "${REPORT_DIR}/report.md" <<EOF
# 500K Validation Report

Run timestamp: $(date '+%Y-%m-%d %H:%M:%S %Z')

## Target

- documents: ${TARGET_DOCS}
- benchmark requests: ${BENCH_REQUESTS}
- benchmark concurrency: ${BENCH_CONCURRENCY}
- content variants: ${CONTENT_VARIANTS}
- fast validation mode: ${FAST_VALIDATION_MODE}
- solr batch size: ${SOLR_BATCH_SIZE}
- solr commit interval ms: ${SOLR_COMMIT_INTERVAL_MS}
- kafka topic: ${RUN_TOPIC}
- kafka consumer group: ${RUN_GROUP_ID}

## Indexing Completion

- Solr count reached: ${solr_count}
- vector_metadata count reached: ${vector_count}

## Artifacts

- Load output: \`${LOAD_OUT_DIR}\`
- Cold benchmark raw files: \`${COLD_OUT_DIR}\`
- Warm benchmark raw files: \`${WARM_OUT_DIR}\`

## Notes

- Cold and warm benchmark summaries are printed by \`scripting/benchmark-hybrid.sh\`.
- Use the warm pass as the steady-state signal and cold pass as startup/cache signal.
EOF

echo "Validation report generated: ${REPORT_DIR}/report.md"
