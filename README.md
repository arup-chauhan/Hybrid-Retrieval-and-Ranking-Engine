# Hybrid Retrieval and Ranking Engine

A production-ready distributed search platform that combines lexical and semantic retrieval, then fuses both signals into a single ranked response.

## Table of Contents

- [Overview](#overview)
- [What This System Delivers](#what-this-system-delivers)
- [Architecture](#architecture)
- [Coordination and Cluster Management](#coordination-and-cluster-management)
- [Services](#services)
- [Data and Query Flow](#data-and-query-flow)
- [APIs](#apis)
- [Storage Layer](#storage-layer)
- [Observability and SLOs](#observability-and-slos)
- [Performance Profile](#performance-profile)
- [Local Deployment](#local-deployment)
- [Kubernetes Deployment](#kubernetes-deployment)
- [Batch and Refresh Workflows](#batch-and-refresh-workflows)
- [Repository Layout](#repository-layout)

## Overview

Hybrid Retrieval and Ranking Engine is designed for high-quality search over large datasets by combining:

- Sparse lexical retrieval (BM25 on SolrCloud)
- Dense semantic retrieval (pgvector on PostgreSQL)
- Application-layer hybrid ranking and fusion
- Distributed microservice execution with gateway, caching, orchestration, and monitoring

The platform supports 500K+ documents and targets p95 end-to-end hybrid latency under 200ms in the defined benchmark envelope.

## What This System Delivers

- Hybrid lexical + semantic retrieval for stronger precision and recall
- Faceted and metadata-aware query handling
- Real-time ingestion and indexing pipeline via Kafka
- Query-time hybrid fusion and ranking controls
- Redis caching for hot-query acceleration
- REST and gRPC interfaces for service and client integration
- Kubernetes-native deployment model with health checks, scaling, and stateful infrastructure
- Full observability with Prometheus metrics, Grafana dashboards, and alerting rules

## Architecture

![Hybrid System Architecture](design_diagrams/Hybrid.jpg)

The runtime is split into retrieval, ranking, and platform layers:

- Retrieval layer: query service, SolrCloud, vector service, PostgreSQL + pgvector, Ollama embeddings
- Ranking layer: ranking and fusion services for score normalization and final ordering
- Platform layer: gateway, orchestration, aggregation, caching, monitoring, and metadata services

Stateful infrastructure is deployed with Kubernetes primitives and service-level routing.

## Coordination and Cluster Management

Coordination responsibilities in this architecture:

- `ZooKeeper` coordinates SolrCloud cluster state, node membership, and collection/config metadata.
- `ZooKeeper` is also used by Kafka in the current deployment mode for broker metadata coordination.
- `Kubernetes` orchestrates service deployments, networking, health probes, scaling policies, and stateful runtime primitives.

Operationally, this means retrieval/index services rely on Kubernetes for workload orchestration, while SolrCloud/Kafka coordination flows through ZooKeeper where required by the current stack.

## Services

Core services in the platform:

- `ingestion-service`: accepts and validates incoming documents
- `indexing-service`: consumes ingestion events and updates retrieval indexes
- `query-service`: executes hybrid fan-out and returns unified search responses
- `vector-service`: embedding + vector retrieval over pgvector
- `ranking-service`: ranking feature extraction and ranking strategy execution
- `fusion-service`: lexical/semantic score normalization and fusion
- `aggregation-service`: multi-source result aggregation and shaping
- `caching-service`: Redis-backed query/result caching
- `metadata-service`: metadata persistence and lookup
- `gateway-service`: edge routing, retries, auth enforcement, and fallback behavior
- `monitoring-service`: metrics exposure and runtime health signal surface
- `orchestration-service`: pipeline coordination and scheduled workflows

## Data and Query Flow

Ingestion flow:

1. Client sends document payload to ingestion service.
2. Ingestion service publishes events to Kafka.
3. Indexing service consumes events and updates Solr and metadata/vector stores.

Query flow:

1. Client sends search request to gateway/query path.
2. Query service executes parallel lexical and semantic retrieval.
3. Ranking/fusion services merge signals into final ranked top-k response.
4. Cache is populated/checked for repeated query acceleration.
5. Query and stage metrics are emitted for observability.

## APIs

### REST

- `POST /search` - hybrid search request
- `GET /facets` - facet and filter metadata retrieval
- `GET /health` - service health checks
- Domain APIs for ingestion, ranking, fusion, metadata, and orchestration operations

Example:

```bash
curl -X POST http://localhost:8083/search \
  -H "Content-Type: application/json" \
  -d '{"query":"wireless headphones","topK":20}'
```

### gRPC

gRPC contracts are available for high-throughput service-to-service and external client integration where low-latency binary transport is preferred.

## Storage Layer

- SolrCloud: distributed BM25 index, faceting, and structured filter execution
- PostgreSQL + pgvector: semantic vectors and ANN-style nearest-neighbor retrieval
- Redis: low-latency cache for repeated hybrid query results
- Metadata persistence: document metadata, vector linkage, and query logging

Representative schema entities:

- `documents`
- `vector_metadata`
- `query_logs`

## Observability and SLOs

Observability assets included:

- Prometheus scrape config: `observability/prometheus/prometheus.yml`
- Alert rules: `observability/alerts/hybrid-alert-rules.yml`
- Grafana datasource provisioning: `observability/grafana/datasources/prometheus-datasource.yaml`
- Grafana dashboard provisioning: `observability/grafana/dashboards/dashboards.yaml`
- Dashboard JSON: `observability/grafana/dashboards/hybrid-overview.json`

Key runtime metrics:

- `hybrid_query_count_total`
- `solr_query_latency_ms`
- `vector_query_latency_ms`
- `ranking_merge_duration_ms`
- `cache_hit_count_total`
- `cache_miss_count_total`

Primary service objectives:

- Hybrid search p95 latency under 200ms
- Error rate under 1%
- Stable cache hit ratio under sustained mixed-query load

## Performance Profile

Validated benchmark profile:

- Dataset: 500,000+ documents
- Query mix: lexical+semantic hybrid dominant
- Concurrency: 20 virtual users
- Sustained load: 10-15 QPS
- Result size: top-k = 20
- SLO: p95 hybrid latency < 200ms

Latency budget model:

- Embedding generation: <= 60ms
- Solr retrieval: <= 50ms
- pgvector retrieval: <= 50ms
- Fusion/ranking/serialization: <= 40ms

## Local Deployment

Prerequisites:

- Java 17+
- Maven 3.9+
- Docker + Docker Compose

Build and run:

```bash
git clone https://github.com/Arup-Chauhan/Hybrid-Retrieval-and-Ranking-Engine.git
cd Hybrid-Retrieval-and-Ranking-Engine
mvn clean package
docker-compose up --build -d
```

Stop local stack:

```bash
docker-compose down
```

## Kubernetes Deployment

Kubernetes manifests are provided under `k8s/` for:

- SolrCloud and coordination services
- PostgreSQL + pgvector init
- Ollama and model bootstrap
- Redis
- Application service deployments and services
- Monitoring components

Deploy:

```bash
kubectl apply -f k8s/
```

## Batch and Refresh Workflows

Apache Airflow is used for batch and refresh workflows that are not part of the online query path.

Primary workflow:

- `embedding_refresh` (manual or scheduled trigger)
  - Select documents requiring vector refresh
  - Generate embeddings via Ollama
  - Upsert embeddings into PostgreSQL + pgvector
  - Refresh related metadata/index references
  - Emit workflow summary metrics and status

Additional refresh workflows:

- `index_refresh`: Solr index/schema-driven refresh jobs
- `metadata_refresh`: metadata/facet recomputation
- `cache_refresh`: Redis invalidation/warmup after retrieval/ranking changes
- `quality_refresh`: retrieval/ranking evaluation pass (for example Recall@K, NDCG)

Design boundary:

- Airflow handles batch control-plane jobs.
- Real-time request orchestration remains in gateway/query/ranking services.

## Repository Layout

- `aggregation-service/`
- `caching-service/`
- `fusion-service/`
- `gateway-service/`
- `indexing-service/`
- `ingestion-service/`
- `k8s/`
- `metadata-service/`
- `monitoring-service/`
- `observability/`
- `orchestration-service/`
- `query-service/`
- `ranking-service/`
- `vector-service/`
- `docker-compose.yaml`
- `pom.xml`
