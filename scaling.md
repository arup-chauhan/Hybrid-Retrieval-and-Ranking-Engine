# Scaling

This file records all scaling-related steps, experiments, and outcomes.

## Update rule (every next scaling step)

For each step add:

- Date/time
- Goal
- Change made
- Commands run
- Result
- Next action

## Benchmark definitions

- `cold run`: first benchmark pass after restart/new validation run with no intentional cache warmup.
- `warm run`: second immediate benchmark pass over the same path after cold run.
- `p95 latency`: 95th percentile response latency from the benchmark output.
- `SLO pass`: benchmark pass is considered passing only when `p95 < 200ms` and error rate `< 1%`.

---

## 2026-02-09 - Step 1: Identify 500K bottlenecks

- Goal: understand why full 500K validation was too slow.
- Change made:
  - Captured partial 500K run progress and stopped run.
- Commands run:
  - `TARGET_DOCS=500000 ... ./scripting/validate-500k-e2e.sh`
- Result:
  - Ingestion progressed to `106,747` requests before stop.
  - Main bottlenecks identified:
    - per-document Solr commit in `indexing-service`
    - per-document embedding call path during indexing
- Next action:
  - implement batched Solr commit and fast validation mode.

## 2026-02-09 - Step 2: Implement indexing throughput improvements

- Goal: make 500K validation practical.
- Change made:
  - Enabled scheduling in indexing app.
  - Reworked Solr indexing to batch add+commit.
  - Added fast validation embedding mode and embedding cache in indexing vector metadata path.
  - Added compose env overrides for scaling settings.
  - Updated loader/validator scripts for content variants and fast-mode bootstrap.
- Files changed:
  - `indexing-service/src/main/java/com/hybrid/indexing/IndexingApplication.java`
  - `indexing-service/src/main/java/com/hybrid/indexing/service/SolrIndexService.java`
  - `indexing-service/src/main/java/com/hybrid/indexing/service/VectorMetadataService.java`
  - `indexing-service/src/main/resources/application.yml`
  - `docker-compose.yaml`
  - `scripting/load-ingestion-dataset.sh`
  - `scripting/validate-500k-e2e.sh`
- Commands run:
  - (pending validation run below)
- Result:
  - Code-level scaling changes implemented.
- Next action:
  - run scaled smoke validation, then full 500K run.

## 2026-02-09 - Step 3: Redis cache path stabilization (supporting scale)

- Goal: ensure repeated query path is accelerated and stable.
- Change made:
  - Integrated query-service with caching-service (Redis-backed cache).
  - Fixed caching-service controller request param binding issue.
  - Fixed caching-service Dockerfile base image and jar path.
  - Fixed Redis host port collision in compose (`6380:6379`).
- Result:
  - Repeated query latency dropped significantly and Redis keys confirmed.
- Next action:
  - proceed with scaled validation run after indexing optimizations.

---

## Pending execution block

- Run smoke-scale check with new indexing settings.
- Run full 500K validation with new settings.
- Record cold/warm benchmark outputs and final decision.

## 2026-02-09 - Step 4: Scaling smoke run + findings

- Goal: validate new scaling path before full 500K rerun.
- Command:
  - `TARGET_DOCS=200 CONCURRENCY=40 ... ./scripting/validate-500k-e2e.sh`
- Result:
  - Ingestion and index sync steps completed.
  - Benchmark still failed at this stage (`p95` above target).
- Finding:
  - Redis cache path itself is working (`CACHE_HIT_REDIS` observed in `query_logs` for repeated query).
  - Failure reason is not cache wiring; likely runtime contention/measurement conditions.
- Next action:
  - fix script correctness to improve scaling-run signal quality.

## 2026-02-09 - Step 5: Script correctness fixes

- Goal: make 500K validator strictly accurate.
- Change made:
  - Forwarded `CONTENT_VARIANTS` env into docker-mode ingestion loader.
  - Updated indexing convergence check to use baseline + delta target counts (instead of absolute target).
- Files changed:
  - `scripting/load-ingestion-dataset.sh`
  - `scripting/validate-500k-e2e.sh`
- Next action:
  - rerun corrected smoke and then full 500K run.

## 2026-02-09 - Step 6: Baseline wait-loop stall root cause + fix

- Goal: stabilize validator convergence logic.
- Symptom:
  - wait loop stalled with unchanged counts after successful ingestion.
- Root cause:
  - repeated runs reused existing doc IDs (`START_ID=1`), causing updates/overwrites rather than net-new count growth.
- Fix:
  - validator now derives `START_ID` from baseline vector count (`baseline + 1`) unless explicitly overridden.
  - loader receives `START_ID` from validator.
- Files changed:
  - `scripting/validate-500k-e2e.sh`
- Next action:
  - rerun corrected smoke and then full 500K run.

## 2026-02-09 - Step 7: Isolate scaling runs from Kafka backlog

- Goal: avoid old-topic backlog contaminating scaling validation.
- Root cause:
  - indexing consumer was reading shared `ingestion-topic` history from previous runs.
- Change made:
  - made indexing topic configurable in listener (`@KafkaListener` uses `${app.kafka.topic}` and `${spring.kafka.consumer.group-id}`).
  - validator now creates unique Kafka topic and unique consumer group per run.
  - validator now restarts both ingestion-service and indexing-service with run-specific topic/group.
- Files changed:
  - `indexing-service/src/main/java/com/hybrid/indexing/consumer/DocumentConsumer.java`
  - `indexing-service/src/main/resources/application.yml`
  - `docker-compose.yaml`
  - `scripting/validate-500k-e2e.sh`
- Next action:
  - rerun smoke validation and confirm counts converge.

## 2026-02-09 - Step 8: Service readiness gate for validator

- Goal: prevent false ingestion failures right after service restart.
- Symptom:
  - ingestion load failed with connection errors immediately after restart.
- Root cause:
  - validator started load before ingestion-service endpoint was reachable.
- Fix:
  - added HTTP readiness wait (`wait_for_http`) for `ingestion-service` before load step.
- File changed:
  - `scripting/validate-500k-e2e.sh`
- Next action:
  - rerun smoke validation.

## 2026-02-09 - Step 9: Readiness probe hardening and payload fix

- Goal: remove flaky startup/probe failures in validator.
- Symptoms:
  - validator sometimes hung on publish-path readiness.
  - repeated `JSON parse error` in ingestion logs during readiness probe.
- Root causes:
  - probe calls had no strict max-time in earlier path and could stall.
  - publish probe payload used escaped JSON inside single quotes (`{\"id\"...}`), sending invalid JSON.
  - actuator endpoint was not a reliable first gate in this stack.
- Fixes made:
  - added explicit timeout caps on readiness curls.
  - changed first gate to `http://localhost:8081/api/ingest` with non-connection check.
  - fixed publish probe payload to valid JSON.
  - switched loader call in validator to `MODE=host` to use localhost path directly.
- File changed:
  - `scripting/validate-500k-e2e.sh`
- Result:
  - readiness stage reliably reached load stage.
- Next action:
  - complete full smoke run and capture report.

## 2026-02-09 - Step 10: High-ID loader fix

- Goal: support large `START_ID` values safely for repeated runs.
- Symptom:
  - loader failed with arithmetic errors like `5.00000e+06`.
- Root cause:
  - host `seq` emitted scientific notation for large values in this shell environment.
- Fix made:
  - replaced `seq` stream with integer shell-loop generator.
- File changed:
  - `scripting/load-ingestion-dataset.sh`
- Result:
  - loader accepted high `START_ID` and completed without request errors.
- Next action:
  - keep large, unique start IDs for every scaling run.

## 2026-02-09 - Step 11: Env-driven Kafka topic/group in service config

- Goal: guarantee run-specific Kafka topic/group are honored at runtime.
- Change made:
  - made ingestion topic explicitly env-driven in YAML.
  - made indexing topic and consumer group explicitly env-driven in YAML.
- Files changed:
  - `ingestion-service/src/main/resources/application.yml`
  - `indexing-service/src/main/resources/application.yml`
- Result:
  - run-time overrides are now explicit and reproducible across compose restarts.
- Next action:
  - use validator run-specific topic/group path for full-scale runs.

## 2026-02-09 - Step 12: Smoke run success after fixes

- Goal: verify end-to-end scaling workflow after all validator/script fixes.
- Command:
  - `START_ID=6000000 TARGET_DOCS=50 CONCURRENCY=20 CONTENT_VARIANTS=32 BENCH_REQUESTS=20 BENCH_CONCURRENCY=10 POLL_INTERVAL_SEC=5 MAX_WAIT_SEC=600 FAST_VALIDATION_MODE=true SOLR_BATCH_SIZE=1000 SOLR_COMMIT_INTERVAL_MS=1000 ./scripting/validate-500k-e2e.sh`
- Artifacts:
  - `docs/benchmarks/500k-2026-02-08-233823/report.md`
  - `docs/benchmarks/500k-2026-02-08-233823/load/`
  - `docs/benchmarks/500k-2026-02-08-233823/cold/`
  - `docs/benchmarks/500k-2026-02-08-233823/warm/`
- Result:
  - ingestion load: 50/50 success, 0 errors.
  - convergence reached:
    - Solr: `2897 / 2896`
    - vector_metadata: `2888 / 2888`
  - benchmark completed but SLO still failed:
    - cold p95: `1003.211 ms`
    - warm p95: `667.497 ms`
- Next action:
  - run full 500K with current stable validator path.
  - then execute latency-optimization workstream (query-path profiling, ANN tuning, cache warming, selective topK reduction).
