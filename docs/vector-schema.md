# Vector Schema Reference

This document describes the PostgreSQL schema expected by `vector-service` for semantic retrieval.

## Required Extension

- `vector` extension (pgvector)

## Tables

### `vector_metadata`

- `document_id` `TEXT` primary key
- `title` `TEXT` nullable
- `embedding` `vector(768)` not null
- `updated_at` `TIMESTAMPTZ` default `NOW()`

Index:
- `hnsw` index on `embedding` with `vector_cosine_ops` (use `WITH (m=16, ef_construction=200)` for fast ANN)

### `query_logs`

- `id` `BIGSERIAL` primary key
- `query_text` `TEXT` not null
- `top_k` `INTEGER` nullable
- `latency_ms` `DOUBLE PRECISION` nullable
- `status` `TEXT` nullable
- `created_at` `TIMESTAMPTZ` default `NOW()`

## Runtime Notes

- `vector-service` computes query embeddings via Ollama.
- It then runs nearest-neighbor lookup against `vector_metadata.embedding`.
- If schema is missing, retrieval will return empty results until initialization is applied.
- Current default model is `embeddinggemma`; keep indexing and query model aligned.
- If embedding dimension changes with a different model, update `vector(...)` schema accordingly.
