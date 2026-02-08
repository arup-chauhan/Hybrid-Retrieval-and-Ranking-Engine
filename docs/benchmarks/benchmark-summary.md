# Benchmark Summary

Timestamp: 2026-02-08 04:11:31 CST

## Configuration

- URL: `http://localhost:8083/search`
- Requests: `120`
- Concurrency: `12`
- topK: `20`
- Targets: `p95 < 200ms`, `error_rate < 1%`

## Cold Run

- File: `docs/benchmarks/cold-run.txt`
- total_requests: `120`
- error_requests: `0`
- error_rate_pct: `0.000`
- avg_latency_ms: `159.219`
- p95_latency_ms: `227.691`
- slo_result: `FAIL` (p95 above target)

## Warm Run

- File: `docs/benchmarks/warm-run.txt`
- total_requests: `120`
- error_requests: `0`
- error_rate_pct: `0.000`
- avg_latency_ms: `174.344`
- p95_latency_ms: `229.224`
- slo_result: `FAIL` (p95 above target)

## Notes

- Runs were captured against local `query-service` process.
- Error rate target passed; p95 latency target did not pass.
