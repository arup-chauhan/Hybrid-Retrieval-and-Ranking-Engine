import { useMemo, useState } from "react";

const MODE_OPTIONS = ["hybrid", "lexical", "semantic"];
const FILTER_OPTIONS = [
  { value: "none", label: "No filter" },
  { value: "solr", label: "Solr only" },
  { value: "vector", label: "Vector only" },
];

export default function Home() {
  const [query, setQuery] = useState("wireless headphones");
  const [topK, setTopK] = useState(20);
  const [mode, setMode] = useState("hybrid");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState(null);
  const [filter, setFilter] = useState("none");

  const viewResults = useMemo(() => {
    if (!result) return [];
    const rawResults = Array.isArray(result.results)
      ? result.results
      : Array.isArray(result.rankedResults)
        ? result.rankedResults
        : [];
    if (!rawResults.length) return [];

    const items = [...rawResults];
    if (mode === "lexical") {
      items.sort((a, b) => (b.lexicalScore || 0) - (a.lexicalScore || 0));
    } else if (mode === "semantic") {
      items.sort((a, b) => (b.semanticScore || 0) - (a.semanticScore || 0));
    } else {
      items.sort((a, b) => (b.score || 0) - (a.score || 0));
    }
    return items.slice(0, topK);
  }, [result, mode, topK]);

  async function onSearch(e) {
    e.preventDefault();
    setLoading(true);
    setError("");

    try {
      const response = await fetch(`/api/search`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-Trace-Id": `frontend-${Date.now()}`
        },
      body: JSON.stringify({ query, topK, mode, filter })
      });

      if (!response.ok) {
        throw new Error(`Query failed with status ${response.status}`);
      }

      const payload = await response.json();
      setResult(payload);
    } catch (err) {
      setError(err.message || "Request failed");
      setResult(null);
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="page">
      <section className="panel">
        <h1>Hybrid Retrieval Console</h1>
        <p className="sub">Next.js UI for hybrid search and score-mode inspection.</p>

        <form onSubmit={onSearch} className="form">
          <label>
            Query
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search term"
              required
            />
          </label>

          <label>
            Top K
            <input
              type="number"
              min="1"
              max="100"
              value={topK}
              onChange={(e) => setTopK(Number(e.target.value) || 1)}
            />
          </label>

          <label>
            Mode
            <select value={mode} onChange={(e) => setMode(e.target.value)}>
              {MODE_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </label>

          <label>
            Filter
            <select value={filter} onChange={(e) => setFilter(e.target.value)}>
              {FILTER_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>

          <button type="submit" disabled={loading}>
            {loading ? "Searching..." : "Search"}
          </button>
        </form>

        <div className="meta">
          <span>API: /api/search (frontend proxy)</span>
          <span>
            Note: mode biases ranking, filter selects Solr-only or Vector-only hits.
          </span>
        </div>

        {error ? <p className="error">{error}</p> : null}
      </section>

      <section className="results panel">
        <h2>Results</h2>
        {result ? (
          <>
            <p className="sub">
              traceId: {result.traceId || "n/a"} | status: {result.status || "n/a"}
              {result.message ? ` | message: ${result.message}` : ""}
            </p>
            <ul>
              {viewResults.map((item, idx) => (
                <li key={`${item.id}-${idx}`}>
                  <div className="row">
                    <strong>{item.title || item.id}</strong>
                    <span>#{idx + 1}</span>
                  </div>
                  <div className="scores">
                    <span>hybrid: {(item.score || 0).toFixed(4)}</span>
                    <span>lexical: {(item.lexicalScore || 0).toFixed(4)}</span>
                    <span>semantic: {(item.semanticScore || 0).toFixed(4)}</span>
                  </div>
                  <small>{item.id}</small>
                </li>
              ))}
            </ul>
          </>
        ) : (
          <p className="sub">Run a search to view ranked results.</p>
        )}
      </section>
    </main>
  );
}
