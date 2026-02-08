#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if ! command -v grpcurl >/dev/null 2>&1; then
  echo "grpcurl is required. Install it first."
  exit 1
fi

echo "==> Vector gRPC: Search"
grpcurl -plaintext \
  -import-path "${ROOT_DIR}/vector-service/src/main/proto" \
  -proto vector_search.proto \
  -d '{"query":"hybrid search","top_k":3}' \
  localhost:9094 VectorSearchService/Search

echo
echo "==> Query gRPC: HybridSearch"
grpcurl -plaintext \
  -import-path "${ROOT_DIR}/query-service/src/main/proto" \
  -proto hybrid_query.proto \
  -d '{"query":"content:hybrid","top_k":5}' \
  localhost:9093 HybridQueryService/HybridSearch

echo
echo "==> Query gRPC: Facets"
grpcurl -plaintext \
  -import-path "${ROOT_DIR}/query-service/src/main/proto" \
  -proto hybrid_query.proto \
  -d '{"field":"metadata","limit":10}' \
  localhost:9093 HybridQueryService/Facets
