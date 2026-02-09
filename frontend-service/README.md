# frontend-service

Next.js frontend for Hybrid Retrieval and Ranking Engine.

## Local run

```bash
npm install
npm run dev
```

Default URL: `http://localhost:3000`

## API configuration

Set query API base:

```bash
NEXT_PUBLIC_QUERY_API_BASE=http://localhost:18083
```

## Vercel deployment

1. Import `frontend-service` as a Vercel project.
2. Set env var `NEXT_PUBLIC_QUERY_API_BASE` to your public query/gateway endpoint.
3. Deploy.
