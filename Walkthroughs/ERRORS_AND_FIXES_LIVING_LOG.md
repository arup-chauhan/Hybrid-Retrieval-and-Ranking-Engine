# Errors And Fixes Living Log

Purpose: keep a running record of major implementation/runtime errors, root causes, and verified fixes.

## How to update this log (for every next step)

1. Add one new section per issue using the template below.
2. Include exact file paths changed.
3. Include one verification command/output summary.
4. Keep newest entries at the top.

Entry template:

- Date:
- Context:
- Symptom:
- Root cause:
- Fix:
- Files changed:
- Verification:
- Follow-up action (if any):

---

## 2026-02-09 - Frontend `Failed to fetch` due build-time API base drift

- Context: bringing up `frontend-service` (Next.js) for live search UI.
- Symptom:
  - UI showed `Failed to fetch`.
  - UI banner showed stale API value `http://localhost:18083` even when backend was reachable on `8083`.
  - `http://localhost:8083/actuator/health` returned `{"status":"UP"}` and direct `POST /search` worked.
- Root cause:
  - frontend was using `NEXT_PUBLIC_QUERY_API_BASE`, which is injected at Next.js build time.
  - runtime compose/env changes did not update already-built client bundle.
  - user expected runtime env swap to change client-side base URL.
- Fix:
  - moved UI calls to same-origin API route: `POST /api/search`.
  - added Next.js server-side proxy at `frontend-service/pages/api/search.js`.
  - set compose env to runtime upstream `QUERY_API_BASE=http://query-service:8083`.
  - updated frontend docs/README env naming from `NEXT_PUBLIC_QUERY_API_BASE` to `QUERY_API_BASE`.
- Files changed:
  - `frontend-service/pages/index.js`
  - `frontend-service/pages/api/search.js`
  - `docker-compose.yaml`
  - `frontend-service/README.md`
  - `README.md`
- Verification:
  - rebuilt frontend container and confirmed UI now displays `API: /api/search (frontend proxy)`.
  - backend health remained `UP` and search endpoint reachable by curl.
- Follow-up action:
  - prefer server-side proxy for browser-to-service calls to avoid CORS/build-time env drift.

## 2026-02-09 - Custom domain data ingestion workflow added (scrape -> file -> ingest)

- Context: need domain-specific datasets (for example Hot Wheels / FIFA 26) instead of synthetic generator text.
- Symptom:
  - synthetic load script generated fixed text pattern; domain queries returned empty results.
- Root cause:
  - pipeline lacked file-based ingestion tooling and upstream dataset preparation path.
- Fix:
  - added website scraper with URL/depth controls and JSONL/CSV export.
  - added file ingestion loader for REST ingest endpoint with concurrency and status artifacts.
  - documented workflow in README under `Custom Search Base`.
- Files changed:
  - `scripting/web_dataset_scraper.py`
  - `scripting/load-ingestion-file.py`
  - `scripting/load-ingestion-file.sh`
  - `README.md`
- Verification:
  - python syntax checks passed.
  - commands documented for scrape -> ingest -> query flow.
- Follow-up action:
  - add dataset-specific presets (Hot Wheels / FIFA) once source URLs and crawl patterns are finalized.

## 2026-02-09 - Redis cache integration errors

- Context: wiring query-service to caching-service for Redis-backed live query caching.
- Symptom:
  - `caching-service` returned HTTP 500 for `/cache/put` and `/cache/get`.
  - Redis key count did not increase.
  - `redis` container initially failed to start due to host port conflict.
- Root cause:
  - Controller request params had implicit names and failed without `-parameters` metadata.
  - Docker host port `6379` already allocated by another local process.
  - Caching Dockerfile used invalid base image + wrong jar path.
- Fix:
  - Added explicit `@RequestParam("key")` / `@RequestParam("ttl")`.
  - Switched Redis host mapping to `6380:6379`.
  - Updated caching Dockerfile to `eclipse-temurin:17-jre` and `target/caching-service-1.0.0.jar`.
- Files changed:
  - `caching-service/src/main/java/com/hybrid/caching/controller/CacheController.java`
  - `caching-service/Dockerfile`
  - `docker-compose.yaml`
- Verification:
  - Direct `POST /cache/put` returned `Cached successfully`.
  - `redis-cli DBSIZE` increased.
  - Repeated `/search` calls showed significant latency drop on second request and Redis key count incremented.
- Follow-up action:
  - Keep Redis cache hit/miss monitoring in query benchmarks.

## 2026-02-08 - 500K validation run throughput bottleneck

- Context: first full 500K end-to-end validation execution.
- Symptom:
  - Loader progressed too slowly for practical completion window.
  - Run stopped after partial ingestion (`106,747` requests recorded).
- Root cause:
  - Per-document Solr commit in indexing path.
  - Per-document Ollama embedding call in vector metadata path.
- Fix:
  - Added reproducible scripts and staged validation workflow so we can optimize iteratively with evidence:
    - bulk ingestion loader
    - indexing convergence checks (Solr + vector_metadata)
    - cold/warm benchmark passes
    - report artifact generation
- Files changed:
  - `scripting/load-ingestion-dataset.sh`
  - `scripting/validate-500k-e2e.sh`
  - `scripting/benchmark-hybrid.sh`
- Verification:
  - Small smoke run completed end-to-end with generated report under `docs/benchmarks/500k-<timestamp>/report.md`.
- Follow-up action:
  - Implement bulk indexing mode to make full 500K run practical.

## 2026-02-08 - Query-service startup failure after Solr timeout change

- Context: added timeout config in `SolrLexicalSearchClient`.
- Symptom:
  - `query-service` failed at startup with `No default constructor found` for `SolrLexicalSearchClient`.
- Root cause:
  - Spring constructor selection ambiguity after adding overload.
- Fix:
  - Marked value-injected constructor as `@Autowired` and preserved compatibility constructor used by tests.
- Files changed:
  - `query-service/src/main/java/com/hybrid/query/service/SolrLexicalSearchClient.java`
- Verification:
  - Service started normally and query probes returned `200`.
- Follow-up action:
  - Prefer explicit constructor injection annotations when adding overloads.

## 2026-02-08 - Docker benchmark network mismatch

- Context: introduced Docker-mode benchmark execution.
- Symptom:
  - Benchmark ran in wrong compose network and could not resolve `query-service`.
- Root cause:
  - Network auto-detection picked the first `_default` network from another project.
- Fix:
  - Added project-slug based preferred network detection with explicit `DOCKER_NETWORK` override.
- Files changed:
  - `scripting/benchmark-hybrid.sh`
- Verification:
  - Docker mode benchmark resolved service DNS and executed successfully.
- Follow-up action:
  - Always support explicit network override in portable scripts.

## 2026-02-08 - Benchmark access issue from host shell

- Context: benchmark script defaulted to host URL.
- Symptom:
  - Host-shell requests to `localhost:8083` failed in sandbox environment.
- Root cause:
  - Environment-level network isolation from host loopback.
- Fix:
  - Added Docker-network execution mode so benchmarks run from inside compose network.
- Files changed:
  - `scripting/benchmark-hybrid.sh`
- Verification:
  - In-network cold/warm benchmark passes produced valid summaries and artifacts.
- Follow-up action:
  - Use Docker mode by default for reproducible local validation.

## 2026-02-08 - Metadata persistence ownership mismatch

- Context: query logging location in architecture.
- Symptom:
  - `query_logs` written by `vector-service`, not by query orchestration layer.
- Root cause:
  - Persistence ownership drift over iterative implementation.
- Fix:
  - Moved `query_logs` writes to `query-service` and removed vector-side logging.
- Files changed:
  - `query-service/src/main/java/com/hybrid/query/service/QueryService.java`
  - `query-service/src/main/java/com/hybrid/query/service/QueryLogService.java`
  - `query-service/src/main/java/com/hybrid/query/config/DatabaseConfig.java`
  - `vector-service/src/main/java/com/hybrid/vector/service/VectorSearchService.java`
- Verification:
  - Targeted module tests/builds passed and runtime writes observed through query path.
- Follow-up action:
  - Keep persistence ownership explicit in README + TODO state.

## 2026-02-08 - Solr/collection runtime mismatches

- Context: initial SolrCloud bring-up and query wiring.
- Symptom:
  - Collection init and query behavior inconsistent due to naming/storage mismatches.
- Root cause:
  - Drift between `hybrid_core` and `hybrid_collection`; Solr volume mount issue.
- Fix:
  - Standardized to `hybrid_collection` across services/manifests.
  - Fixed Solr volume mapping for stable collection bootstrap.
- Files changed:
  - `query-service/src/main/resources/application.yml`
  - `indexing-service/src/main/resources/application.yml`
  - `k8s/services/query/deployment.yaml`
  - `k8s/solrcloud/init-collection-job.yaml`
  - `docker-compose.yaml`
- Verification:
  - Solr collection creation/list succeeded; query/indexing paths functional.
- Follow-up action:
  - Keep one canonical collection name across code + deploy manifests.

---

## Future policy

For every new step, add the issue to this file immediately after fix verification.

## 2026-02-09 - 500k validator convergence stall after ingestion

- Context: paced 500k validator smoke.
- Symptom:
  - ingestion step completed, but convergence loop stayed at `solr=0/target` while `vector_metadata` kept increasing.
- Root cause:
  - `indexing-service` custom `KafkaConsumerConfig` did not set `ConsumerConfig.AUTO_OFFSET_RESET_CONFIG`.
  - Effective consumer behavior could default to `latest` for new run-topic/group startup timing.
  - validator started load after ingestion readiness only, without an indexing readiness gate.
- Fix:
  - set `AUTO_OFFSET_RESET_CONFIG` from `spring.kafka.consumer.auto-offset-reset`.
  - added validator wait on `http://localhost:${INDEXING_HOST_PORT}/actuator/health` before load.
  - added `INDEXING_HOST_PORT` env plumb-through in validator report/logging.
- Files changed:
  - `indexing-service/src/main/java/com/hybrid/indexing/config/KafkaConsumerConfig.java`
  - `scripting/validate-500k-e2e.sh`
- Verification command:
  - `make validate-500k-paced-smoke VALIDATE_TARGET_DOCS=50 VALIDATE_BENCH_REQUESTS=40`
- Follow-up action:
  - keep explicit consumer offset settings whenever custom Kafka consumer factories are used.

## 2026-02-09 - Validator bootstrap chain issues (network, SolrCloud, ID overlap)

- Context: repeated paced validator smoke runs in a machine with multiple Docker compose projects.
- Symptoms:
  - wrong Docker network selected (`_default` from another project), causing internal DNS failures.
  - `postgres`/query/vector dependencies missing from validator startup path.
  - port conflicts on 8083/8084/9093/9094.
  - Solr indexing failures with `You must type the correct path` because collection bootstrap was missing.
  - vector convergence partial stalls due to duplicate IDs from count-based start ID derivation.
- Fixes:
  - prefer `${COMPOSE_PROJECT_NAME}_default` and validate fallback after startup.
  - include full required dependency set in validator startup.
  - remap query/vector host and gRPC ports in smoke target.
  - bootstrap `hybrid_collection` via SolrCloud create path when absent.
  - switch default `START_ID` to time-based high-water ID generation.
  - make validator smoke target depend on `build` so runtime uses latest packaged jars.
- Files changed:
  - `scripting/validate-500k-e2e.sh`
  - `Makefile`
  - `README.md`
  - `scaling.md`
- Verification:
  - `make validate-500k-paced-smoke VALIDATE_TARGET_DOCS=20 VALIDATE_BENCH_REQUESTS=20`
  - script completed through report generation at:
    - `docs/benchmarks/500k-2026-02-09-042457/report.md`

## 2026-02-09 - Docker Desktop runtime instability during validation loops

- Context: repeated rebuild + compose up + paced validator smoke runs.
- Symptoms:
  - `failed to create temp dir ... input/output error` during image build.
  - subsequent compose runs failed with `Docker Desktop is unable to start`.
- Impact:
  - blocked final verification pass for prewarm-enabled cold benchmark.
- Workaround:
  - restart Docker Desktop and rerun:
    - `make validate-500k-paced-smoke VALIDATE_TARGET_DOCS=20 VALIDATE_BENCH_REQUESTS=20`
  - if failure persists, clear Docker Desktop resources and retry.
