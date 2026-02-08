# Demo Script

This script gives a fast, public-facing walkthrough of ingestion, hybrid retrieval, and ranking/fusion endpoints.

## 1) Start the stack

```bash
mvn clean package
docker-compose up --build -d
```

Expected:
- All containers show `Up` in `docker-compose ps`.

## 2) Verify platform health

```bash
curl -s http://localhost:9090/health
```

Expected:
- JSON includes `{"status":"UP"}`.

## 3) Ingest a sample document

```bash
curl -s -X POST http://localhost:8081/api/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "id":"doc-1001",
    "title":"Hybrid search with lexical and semantic signals",
    "content":"This document explains BM25, dense vectors, and score fusion.",
    "metadata":"{\"category\":\"search\",\"lang\":\"en\"}"
  }'
```

Expected:
- Response body: `Document ingested successfully`.

## 4) Run hybrid search

```bash
curl -s -X POST http://localhost:8083/search \
  -H "Content-Type: application/json" \
  -d '{"query":"lexical and semantic ranking"}'
```

Expected:
- JSON response with fields like:
  - `message`
  - `solrResult`
  - `vectorResult`

## 5) Optional ranking and fusion smoke checks

Ranking:

```bash
curl -s -X POST http://localhost:8085/api/rank \
  -H "Content-Type: application/json" \
  -d '[
    {"id":"doc-1001","score":0.81},
    {"id":"doc-1002","score":0.74}
  ]'
```

Fusion:

```bash
curl -s -X POST http://localhost:8087/api/fusion/combine \
  -H "Content-Type: application/json" \
  -d '{
    "lexical":[{"id":"doc-1001","score":12.1}],
    "semantic":[{"id":"doc-1001","score":0.82}]
  }'
```

Expected:
- Ranking returns a ranked list payload.
- Fusion returns a combined result payload.

## Stop the stack

```bash
docker-compose down
```
