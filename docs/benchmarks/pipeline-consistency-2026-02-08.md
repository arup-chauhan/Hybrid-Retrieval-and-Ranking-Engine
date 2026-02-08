# Pipeline Consistency Under Load (2026-02-08)

## Scope

Verified `ingestion -> indexing -> query` runtime consistency using the hybrid query benchmark in Docker network mode.

## Command

```bash
MODE=docker REQUESTS=100 CONCURRENCY=20 OUT_DIR=docs/benchmarks/latest ./scripting/benchmark-hybrid.sh
MODE=docker REQUESTS=100 CONCURRENCY=20 OUT_DIR=docs/benchmarks/latest-warm ./scripting/benchmark-hybrid.sh
```

## Results

Cold pass (first run):

- `total_requests: 100`
- `error_requests: 0`
- `error_rate_pct: 0.000`
- `avg_latency_ms: 463.854`
- `p95_latency_ms: 1402.819`
- `slo_result: FAIL`

Warm pass (steady-state):

- `total_requests: 100`
- `error_requests: 0`
- `error_rate_pct: 0.000`
- `avg_latency_ms: 59.504`
- `p95_latency_ms: 111.356`
- `slo_result: PASS`

## Notes

- Docker mode in `scripting/benchmark-hybrid.sh` now auto-runs inside compose network.
- Raw run artifacts are stored in:
  - `docs/benchmarks/latest/`
  - `docs/benchmarks/latest-warm/`
