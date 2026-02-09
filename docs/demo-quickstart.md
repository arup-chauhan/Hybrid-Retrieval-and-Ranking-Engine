# Demo Quickstart (Team + Vercel)

## 1) Start core stack

```bash
docker-compose -p hybrid-retrieval-and-ranking-engine up --build -d \
  zookeeper kafka solr postgres redis ollama \
  ingestion-service indexing-service vector-service caching-service query-service frontend-service
```

## 2) Load demo dataset (5k)

```bash
./scripting/run_5k_doc_engine.sh
```

This prints real-time progress and writes artifacts under `docs/benchmarks/500k-<timestamp>/`.

## 3) Search from CLI (curl)

```bash
curl -X POST http://localhost:18083/search \
  -H "Content-Type: application/json" \
  -d '{"query":"wireless headphones","topK":20}'
```

## 4) Search from UI

- Open: `http://localhost:3001`
- Use mode toggle (`hybrid`, `lexical`, `semantic`) to inspect score-based result ordering.

## 5) Vercel hosting (frontend only)

Deploy `frontend-service` as a Vercel project and set:

- `NEXT_PUBLIC_QUERY_API_BASE=https://<your-public-query-or-gateway-endpoint>`

Notes:

- Frontend is optional. Engine works fully with curl/API clients.
- Backend endpoint for Vercel must be internet-accessible and CORS-enabled.
