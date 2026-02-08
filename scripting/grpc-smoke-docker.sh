#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NETWORK_NAME="hybrid-retrieval-and-ranking-engine_default"
IMAGE="fullstorydev/grpcurl:latest"

echo "==> Ensuring grpcurl image is available"
docker image inspect "${IMAGE}" >/dev/null 2>&1 || docker pull "${IMAGE}" >/dev/null

echo "==> Vector gRPC: Search"
docker run --rm \
  --network "${NETWORK_NAME}" \
  -v "${ROOT_DIR}:/work" \
  -w /work \
  "${IMAGE}" \
  -plaintext \
  -import-path /work/vector-service/src/main/proto \
  -proto vector_search.proto \
  -d '{"query":"hybrid search","top_k":3}' \
  vector-service:9094 VectorSearchService/Search

echo
echo "==> Query gRPC: HybridSearch"
docker run --rm \
  --network "${NETWORK_NAME}" \
  -v "${ROOT_DIR}:/work" \
  -w /work \
  "${IMAGE}" \
  -plaintext \
  -import-path /work/query-service/src/main/proto \
  -proto hybrid_query.proto \
  -d '{"query":"content:hybrid","top_k":5}' \
  query-service:9093 HybridQueryService/HybridSearch

echo
echo "==> Query gRPC: Facets"
docker run --rm \
  --network "${NETWORK_NAME}" \
  -v "${ROOT_DIR}:/work" \
  -w /work \
  "${IMAGE}" \
  -plaintext \
  -import-path /work/query-service/src/main/proto \
  -proto hybrid_query.proto \
  -d '{"field":"metadata","limit":10}' \
  query-service:9093 HybridQueryService/Facets
