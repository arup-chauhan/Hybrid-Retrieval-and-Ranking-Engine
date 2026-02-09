# frontend-service

Next.js frontend for Hybrid Retrieval and Ranking Engine.

## Local run

```bash
npm install
npm run dev
```

Default URL: `http://localhost:3000`

## API configuration

Set frontend proxy upstream:

```bash
QUERY_API_BASE=http://query-service:8083
```

## Vercel deployment

1. Import `frontend-service` as a Vercel project.
2. Set env var `QUERY_API_BASE` to your public query/gateway endpoint.
3. Deploy.
