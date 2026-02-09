export default async function handler(req, res) {
  if (req.method !== "POST") {
    res.setHeader("Allow", "POST");
    return res.status(405).json({ error: "Method not allowed" });
  }

  const apiBase = process.env.QUERY_API_BASE || "http://query-service:8083";

  try {
    const upstream = await fetch(`${apiBase}/search`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Trace-Id": req.headers["x-trace-id"] || `frontend-${Date.now()}`,
      },
      body: JSON.stringify(req.body || {}),
    });

    const text = await upstream.text();
    res.status(upstream.status);
    res.setHeader("Content-Type", upstream.headers.get("content-type") || "application/json");
    return res.send(text);
  } catch (err) {
    return res.status(502).json({ error: "Upstream query-service unreachable", detail: String(err) });
  }
}
