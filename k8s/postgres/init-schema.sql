CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS vector_metadata (
    document_id TEXT PRIMARY KEY,
    title TEXT,
    embedding vector(768) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_vector_metadata_embedding_ivfflat
    ON vector_metadata
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

CREATE TABLE IF NOT EXISTS query_logs (
    id BIGSERIAL PRIMARY KEY,
    query_text TEXT NOT NULL,
    top_k INTEGER,
    latency_ms DOUBLE PRECISION,
    status TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS documents (
    id TEXT PRIMARY KEY,
    title TEXT,
    content TEXT,
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS document_metadata (
    id TEXT PRIMARY KEY,
    title TEXT,
    category TEXT,
    indexed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
