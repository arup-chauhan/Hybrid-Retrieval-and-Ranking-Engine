# Global Makefile for Hybrid Retrieval and Ranking Engine

.PHONY: build up down clean logs benchmark benchmark-docker benchmark-slo-cold benchmark-slo-warm benchmark-slo benchmark-smoke-ci validate-500k-paced-smoke

COMPOSE_PROJECT_NAME ?= hybrid-retrieval-and-ranking-engine
BENCH_REQUESTS ?= 200
BENCH_CONCURRENCY ?= 20
BENCH_TOPK ?= 20
SLO_RATE_QPS ?= 12
SMOKE_RATE_QPS ?= 8
SMOKE_REQUESTS ?= 40
SMOKE_CONCURRENCY ?= 5
BENCH_OUT_BASE ?= docs/benchmarks
VALIDATE_TARGET_DOCS ?= 1000
VALIDATE_LOAD_CONCURRENCY ?= 20
VALIDATE_BENCH_REQUESTS ?= 80
VALIDATE_BENCH_CONCURRENCY ?= 20
VALIDATE_BENCH_RATE_QPS ?= 12

build:
	@echo "Building all services..."
	mvn clean package -DskipTests

up: build
	@echo "Starting all containers..."
	docker-compose -p $(COMPOSE_PROJECT_NAME) up --build -d

down:
	@echo "Stopping all containers..."
	docker-compose -p $(COMPOSE_PROJECT_NAME) down

clean:
	@echo "Cleaning build artifacts..."
	mvn clean
	docker system prune -f

logs:
	@echo "Viewing logs..."
	docker-compose -p $(COMPOSE_PROJECT_NAME) logs -f

benchmark:
	@echo "Running hybrid benchmark..."
	./scripting/benchmark-hybrid.sh

benchmark-docker:
	@echo "Running benchmark in Docker network mode..."
	MODE=docker \
	COMPOSE_PROJECT_NAME=$(COMPOSE_PROJECT_NAME) \
	REQUESTS=$(BENCH_REQUESTS) \
	CONCURRENCY=$(BENCH_CONCURRENCY) \
	TOPK=$(BENCH_TOPK) \
	OUT_DIR=$(BENCH_OUT_BASE)/latest \
	./scripting/benchmark-hybrid.sh

benchmark-slo-cold:
	@echo "Running paced SLO cold benchmark (RATE_QPS=$(SLO_RATE_QPS))..."
	MODE=docker \
	COMPOSE_PROJECT_NAME=$(COMPOSE_PROJECT_NAME) \
	REQUESTS=240 \
	CONCURRENCY=20 \
	TOPK=20 \
	RATE_QPS=$(SLO_RATE_QPS) \
	OUT_DIR=$(BENCH_OUT_BASE)/latest-slo-qps$(SLO_RATE_QPS)-cold \
	./scripting/benchmark-hybrid.sh

benchmark-slo-warm:
	@echo "Running paced SLO warm benchmark (RATE_QPS=$(SLO_RATE_QPS))..."
	MODE=docker \
	COMPOSE_PROJECT_NAME=$(COMPOSE_PROJECT_NAME) \
	REQUESTS=240 \
	CONCURRENCY=20 \
	TOPK=20 \
	RATE_QPS=$(SLO_RATE_QPS) \
	OUT_DIR=$(BENCH_OUT_BASE)/latest-slo-qps$(SLO_RATE_QPS)-warm \
	./scripting/benchmark-hybrid.sh

benchmark-slo: benchmark-slo-cold benchmark-slo-warm
	@echo "Completed paced SLO benchmark pair."

benchmark-smoke-ci:
	@echo "Running CI-style paced benchmark smoke check..."
	MODE=docker \
	COMPOSE_PROJECT_NAME=$(COMPOSE_PROJECT_NAME) \
	REQUESTS=$(SMOKE_REQUESTS) \
	CONCURRENCY=$(SMOKE_CONCURRENCY) \
	TOPK=20 \
	RATE_QPS=$(SMOKE_RATE_QPS) \
	OUT_DIR=$(BENCH_OUT_BASE)/ci-smoke \
	./scripting/benchmark-hybrid.sh

validate-500k-paced-smoke: build
	@echo "Running paced 500K-validator smoke flow..."
	ZOOKEEPER_HOST_PORT=12181 \
	KAFKA_HOST_PORT=19092 \
	INGESTION_HOST_PORT=18081 \
	INDEXING_HOST_PORT=18082 \
	QUERY_HOST_PORT=18083 \
	QUERY_GRPC_HOST_PORT=19093 \
	VECTOR_HOST_PORT=18084 \
	VECTOR_GRPC_HOST_PORT=19094 \
	PREWARM_BEFORE_COLD=true \
	PREWARM_REQUESTS=3 \
	TARGET_DOCS=$(VALIDATE_TARGET_DOCS) \
	CONCURRENCY=$(VALIDATE_LOAD_CONCURRENCY) \
	BENCH_REQUESTS=$(VALIDATE_BENCH_REQUESTS) \
	BENCH_CONCURRENCY=$(VALIDATE_BENCH_CONCURRENCY) \
	BENCH_RATE_QPS=$(VALIDATE_BENCH_RATE_QPS) \
	FAST_VALIDATION_MODE=true \
	POLL_INTERVAL_SEC=5 \
	MAX_WAIT_SEC=1200 \
	./scripting/validate-500k-e2e.sh
