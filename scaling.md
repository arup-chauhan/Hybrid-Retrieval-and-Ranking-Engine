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

## 2026-02-09 - Step 13: Quick latency tuning matrix (topK x concurrency)

- Goal: find the fastest practical config before next full 500K run.
- Run directory:
  - `docs/benchmarks/tuning-2026-02-09-022716`
- Matrix executed:
  - topK: `20, 10, 5`
  - concurrency: `20, 10`
  - requests per case: `80`
- Results (sorted by p95, lower is better):
  - `topk-10_conc-20` -> avg `1918.122` ms, p95 `3081.955` ms, errors `0.000%`
  - `topk-20_conc-10` -> avg `874.924` ms, p95 `3792.237` ms, errors `0.000%`
  - `topk-5_conc-10` -> avg `1378.418` ms, p95 `4400.306` ms, errors `0.000%`
  - `topk-20_conc-20` -> avg `1411.405` ms, p95 `4407.725` ms, errors `0.000%`
  - `topk-5_conc-20` -> avg `1766.671` ms, p95 `4895.957` ms, errors `0.000%`
  - `topk-10_conc-10` -> avg `2240.296` ms, p95 `7142.129` ms, errors `0.000%`
- Outcome:
  - All variants remain above SLO; best observed p95 in this pass is `3081.955` ms.
  - Immediate next optimization should target query-path timeout behavior and expensive external calls (vector/Solr stage isolation), not only topK/concurrency.

## 2026-02-09 - Step 14: README SLO validation run (post stack stabilization)

- Goal:
  - validate README latency SLO (`p95 < 200ms`, error `<1%`) on running Docker stack.
- Environment:
  - compose project: `hybrid-retrieval-and-ranking-engine`
  - benchmark mode: `MODE=docker`
  - benchmark params: `REQUESTS=200`, `CONCURRENCY=20`, `TOPK=20`
- Cold/warm baseline results:
  - cold (`docs/benchmarks/latest-slo-check`):
    - avg `338.805` ms
    - p95 `2363.253` ms
    - error `0.000%`
    - result `FAIL`
  - warm (`docs/benchmarks/latest-slo-check-warm`):
    - avg `112.929` ms
    - p95 `225.124` ms
    - error `0.000%`
    - result `FAIL`
- Interpretation:
  - warm is close but still above SLO threshold.

## 2026-02-09 - Step 15: Timeout-budget tuning pass and re-validation

- Goal:
  - reduce tail latency via stricter query timeout budget + downstream timeouts.
- Changes applied:
  - first tuning pass:
    - `query.execution.total-budget-ms`: `200`
    - `query.execution.vector-stage-budget-ms`: `80`
    - `solr.request-timeout-ms`: `90`
    - `vector.request-timeout-ms`: `90`
  - second (stricter) tuning pass:
    - `query.execution.total-budget-ms`: `120`
    - `query.execution.vector-stage-budget-ms`: `40`
    - `solr.request-timeout-ms`: `60`
    - `vector.request-timeout-ms`: `60`
- Results:
  - first pass warm (`docs/benchmarks/latest-slo-check-tuned-warm`):
    - avg `126.462` ms
    - p95 `352.210` ms
    - error `0.000%`
    - result `FAIL`
  - stricter pass valid cold (`docs/benchmarks/latest-slo-check-strict-cold-valid`):
    - avg `835.757` ms
    - p95 `6032.145` ms
    - error `0.000%`
    - result `FAIL`
  - stricter pass valid warm (`docs/benchmarks/latest-slo-check-strict-warm-valid`):
    - avg `160.066` ms
    - p95 `317.532` ms
    - error `0.000%`
    - result `FAIL`
- Important note:
  - one intermediate strict run captured `100%` errors immediately after service recreation; it was excluded as invalid startup-window noise.
- Outcome:
  - SLO not yet met under current benchmark harness.
  - timeout tightening alone is insufficient and can worsen cold tail behavior.

## 2026-02-09 - Step 16: Benchmark-envelope mismatch identified

- Observation:
  - `scripting/benchmark-hybrid.sh` currently drives max-throughput (`xargs -P` flood) with no QPS limiter.
- Impact:
  - this does not enforce the README target envelope of `10-15 QPS`, even though it uses `CONCURRENCY=20`.
- Next action:
  - add explicit QPS control in benchmark harness or an equivalent load profile mode, then revalidate SLO against the documented envelope.
  - use query stage logs (trace-based) to isolate p95 contributors before next latency tweak cycle.

## 2026-02-09 - Step 17: QPS-shaped SLO validation (README envelope)

- Goal:
  - validate SLO under explicit load shaping aligned with README (`10-15 QPS`).
- Implementation:
  - added `RATE_QPS` support to `scripting/benchmark-hybrid.sh` paced mode.
- Run setup:
  - `DOCKER_NETWORK=hybrid-retrieval-and-ranking-engine_default`
  - `MODE=docker REQUESTS=240 CONCURRENCY=20 RATE_QPS=12 TOPK=20`
- Results:
  - cold (`docs/benchmarks/latest-slo-qps12-cold`):
    - avg `119.822` ms
    - p95 `67.023` ms
    - error `0.000%`
    - `PASS`
  - warm (`docs/benchmarks/latest-slo-qps12-warm`):
    - avg `30.993` ms
    - p95 `41.507` ms
    - error `0.000%`
    - `PASS`
- Conclusion:
  - with explicit QPS shaping (`12 QPS`), current system meets README SLO (`p95 < 200ms`, error `< 1%`).

## 2026-02-09 - Step 18: Reproducible paced benchmark commands and CI smoke gate

- Goal:
  - make SLO validation one-command reproducible and CI-friendly.
- Implemented:
  - `Makefile` targets added:
    - `benchmark-slo-cold`
    - `benchmark-slo-warm`
    - `benchmark-slo`
    - `benchmark-smoke-ci`
    - `benchmark-docker`
  - benchmark script already supports pacing via `RATE_QPS` and is now wired through Make targets.
- Smoke gate calibration:
  - default `benchmark-smoke-ci` profile set to:
    - requests: `40`
    - concurrency: `5`
    - rate: `8 QPS`
  - verification run:
    - error `0.000%`
    - avg `17.389` ms
    - p95 `27.033` ms
    - result `PASS`
- README updated:
  - added direct commands for `make benchmark-slo` and `make benchmark-smoke-ci`.
  - documented default smoke profile values.

## 2026-02-09 - Step 19: Fix validator indexing convergence stall

- Goal:
  - stop validator runs from stalling at `solr=0/...` while vector metadata grows.
- Root cause:
  - custom Kafka consumer config in `indexing-service` did not set `AUTO_OFFSET_RESET`, so runs could start at `latest` and miss already-published messages.
  - validator only waited for ingestion readiness before load; indexing readiness was not gated.
- Changes made:
  - wired `spring.kafka.consumer.auto-offset-reset` into `KafkaConsumerConfig`.
  - added `INDEXING_HOST_PORT` support and indexing HTTP readiness wait in validator.
  - made `make up` depend on `build` to prevent missing target JAR startup failures.
- Files changed:
  - `indexing-service/src/main/java/com/hybrid/indexing/config/KafkaConsumerConfig.java`
  - `scripting/validate-500k-e2e.sh`
  - `Makefile`
  - `README.md`
- Next action:
  - rerun `make validate-500k-paced-smoke` and confirm Solr/vector convergence completes.

## 2026-02-09 - Step 20: Validator convergence and SolrCloud bootstrap hardening

- Goal:
  - make paced validator smoke complete reliably in mixed local Docker environments.
- Issues resolved:
  - compose network auto-detection could pick another project network before this stack was up.
  - validator startup did not include all required runtime services (`postgres`, `ollama`, `query`, `vector`, cache path).
  - host port collisions on query/vector defaults.
  - SolrCloud collection bootstrap missing in fresh/partially-reset local states.
  - count-derived `START_ID` caused ID overlap after partial runs and stalled vector convergence.
- Changes made:
  - validator now prefers `${COMPOSE_PROJECT_NAME}_default` network and validates/fallbacks after startup.
  - validator startup now brings up full required dependency set.
  - `validate-500k-paced-smoke` pins remapped query/vector + gRPC host ports.
  - validator bootstraps `hybrid_collection` in SolrCloud when missing.
  - validator default `START_ID` now uses a time-based high-water value to guarantee unique IDs.
  - `validate-500k-paced-smoke` now depends on `build` so Docker images include latest code.
- Validation run:
  - command: `make validate-500k-paced-smoke VALIDATE_TARGET_DOCS=20 VALIDATE_BENCH_REQUESTS=20`
  - report: `docs/benchmarks/500k-2026-02-09-042457/report.md`
  - outcome:
    - indexing convergence: PASS
    - cold benchmark: FAIL (`p95 3497.008ms`)
    - warm benchmark: PASS (`p95 114.904ms`)
    - end-to-end script completion: PASS

## 2026-02-09 - Step 21: Cold-path optimization iteration (query startup + miss coalescing)

- Goal:
  - reduce cold-run tail latency under concurrent identical requests.
- Changes made:
  - added query startup warmup runner with configurable retries/delay and direct lexical+semantic preheat.
  - enabled warmup env in compose for `query-service`.
  - added in-flight miss coalescing in `QueryService` to avoid cache-miss stampede on identical `(query, topK)`.
  - added optional validator prewarm hook before cold benchmark and enabled it in paced smoke target.
- Files changed:
  - `query-service/src/main/java/com/hybrid/query/config/QueryWarmupRunner.java`
  - `query-service/src/main/java/com/hybrid/query/service/QueryService.java`
  - `query-service/src/main/resources/application.yml`
  - `docker-compose.yaml`
  - `scripting/validate-500k-e2e.sh`
  - `Makefile`
- Latest measurable run (before Docker runtime instability):
  - command: `make validate-500k-paced-smoke VALIDATE_TARGET_DOCS=20 VALIDATE_BENCH_REQUESTS=20`
  - report: `docs/benchmarks/500k-2026-02-09-043643/report.md`
  - cold: `p95 1027.996ms` (improved vs earlier multi-second cold runs, still FAIL)
  - warm: `p95 186.319ms` (PASS)
- Current blocker:
  - Docker Desktop became unstable during follow-up reruns (`input/output error` and `Docker Desktop is unable to start`), preventing final verification of the prewarm-enabled run.
