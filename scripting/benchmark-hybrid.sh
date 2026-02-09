#!/usr/bin/env sh
set -eu

MODE="${MODE:-host}" # host | docker
URL="${URL:-}"
QUERY="${QUERY:-wireless headphones}"
TOPK="${TOPK:-20}"
REQUESTS="${REQUESTS:-200}"
CONCURRENCY="${CONCURRENCY:-20}"
P95_TARGET_MS="${P95_TARGET_MS:-200}"
ERROR_RATE_TARGET_PCT="${ERROR_RATE_TARGET_PCT:-1}"
OUT_DIR="${OUT_DIR:-/tmp/hybrid-benchmark}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-hybrid-retrieval-and-ranking-engine}"

if [ -z "$URL" ]; then
  if [ "$MODE" = "docker" ]; then
    URL="http://query-service:8083/search"
  else
    URL="http://localhost:8083/search"
  fi
fi

if [ "$MODE" = "docker" ] && [ "${INSIDE_DOCKER_BENCH:-0}" != "1" ]; then
  if ! command -v docker >/dev/null 2>&1; then
    echo "ERROR: docker is required for MODE=docker"
    exit 2
  fi

  ABS_OUT_DIR="$OUT_DIR"
  mkdir -p "$ABS_OUT_DIR"
  ABS_OUT_DIR="$(cd "$ABS_OUT_DIR" && pwd)"
  REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

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

  echo "Running benchmark in Docker network mode"
  echo "  DOCKER_NETWORK=$DOCKER_NETWORK"
  echo "  URL=$URL"

  docker run --rm \
    --network "$DOCKER_NETWORK" \
    -e INSIDE_DOCKER_BENCH=1 \
    -e MODE=host \
    -e URL="$URL" \
    -e QUERY="$QUERY" \
    -e TOPK="$TOPK" \
    -e REQUESTS="$REQUESTS" \
    -e CONCURRENCY="$CONCURRENCY" \
    -e P95_TARGET_MS="$P95_TARGET_MS" \
    -e ERROR_RATE_TARGET_PCT="$ERROR_RATE_TARGET_PCT" \
    -e OUT_DIR=/out \
    -v "$ABS_OUT_DIR:/out" \
    -v "$REPO_ROOT:/workspace:ro" \
    -w /workspace \
    curlimages/curl:8.10.1 \
    sh /workspace/scripting/benchmark-hybrid.sh
  exit $?
fi

mkdir -p "$OUT_DIR"
LAT_FILE="$OUT_DIR/latencies_ms.txt"
RES_FILE="$OUT_DIR/results.txt"
rm -f "$LAT_FILE" "$RES_FILE"

echo "Running benchmark"
echo "  URL=$URL"
echo "  REQUESTS=$REQUESTS CONCURRENCY=$CONCURRENCY TOPK=$TOPK"
echo "  SLO targets: p95<${P95_TARGET_MS}ms, error_rate<${ERROR_RATE_TARGET_PCT}%"

export URL QUERY TOPK LAT_FILE RES_FILE

seq "$REQUESTS" | xargs -I{} -P "$CONCURRENCY" sh -c '
out=$(curl -sS -o /dev/null -w "%{http_code} %{time_total}" \
  -H "Content-Type: application/json" \
  -X POST "$URL" \
  -d "{\"query\":\"$QUERY\",\"topK\":$TOPK}" \
  --connect-timeout 5 --max-time 20 || echo "000 20.000")
code=$(echo "$out" | awk "{print \$1}")
time_s=$(echo "$out" | awk "{print \$2}")
awk -v t="$time_s" "BEGIN { printf \"%.3f\\n\", t*1000.0 }" >> "$LAT_FILE"
echo "$code" >> "$RES_FILE"
'

total=$(wc -l < "$RES_FILE" | tr -d ' ')
errors=$(awk '$1 !~ /^2/ { c++ } END { print c+0 }' "$RES_FILE")
error_pct=$(awk -v e="$errors" -v t="$total" 'BEGIN { if (t==0) print 100; else printf "%.3f", (e*100.0)/t }')

p95=$(sort -n "$LAT_FILE" | awk 'NR==1 {a[NR]=$1} NR>1 {a[NR]=$1} END { if (NR==0) {print 0; exit} idx=int(NR*0.95); if (idx<1) idx=1; printf "%.3f", a[idx] }')
avg=$(awk '{ s+=$1; c++ } END { if (c==0) print 0; else printf "%.3f", s/c }' "$LAT_FILE")

echo ""
echo "Benchmark summary"
echo "  total_requests: $total"
echo "  error_requests: $errors"
echo "  error_rate_pct: $error_pct"
echo "  avg_latency_ms: $avg"
echo "  p95_latency_ms: $p95"

p95_pass=$(awk -v p="$p95" -v t="$P95_TARGET_MS" 'BEGIN { print (p < t) ? 1 : 0 }')
err_pass=$(awk -v e="$error_pct" -v t="$ERROR_RATE_TARGET_PCT" 'BEGIN { print (e < t) ? 1 : 0 }')

if [ "$p95_pass" -eq 1 ] && [ "$err_pass" -eq 1 ]; then
  echo "  slo_result: PASS"
  exit 0
fi

echo "  slo_result: FAIL"
exit 1
