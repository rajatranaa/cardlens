import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import express from "express";
import { z } from "zod";

// --- Config (env vars only; no secrets in code) ---
const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8080";
const BACKEND_API_KEY = process.env.CARDLENS_API_KEY;   // static key for backend auth
const PORT = process.env.MCP_PORT || 3000;

if (!BACKEND_API_KEY) {
  console.error("ERROR: CARDLENS_API_KEY env var is required");
  process.exit(1);
}

// Helper: call the backend, forwarding the static API key header.
async function backend(method, path, { query, body } = {}) {
  const url = new URL(path, BACKEND_URL);
  if (query) {
    for (const [k, v] of Object.entries(query)) {
      if (v !== undefined && v !== null) url.searchParams.set(k, String(v));
    }
  }
  const resp = await fetch(url, {
    method,
    headers: {
      "Content-Type": "application/json",
      "X-Api-Key": BACKEND_API_KEY,
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await resp.text();
  if (!resp.ok) {
    throw new Error(`Backend ${method} ${path} -> ${resp.status}: ${text}`);
  }
  return text ? JSON.parse(text) : null;
}

function buildServer() {
  const server = new McpServer({ name: "cardlens-mcp-server", version: "1.0.0" });

  server.registerTool(
    "fetch_statements",
    {
      title: "Fetch Statements",
      description:
        "Fetch, decrypt, extract, and store all credit card statements for a given month and year. " +
        "Runs the full sync pipeline across every registered card. Idempotent per period.",
      inputSchema: {
        month: z.number().int().min(1).max(12).describe("Statement month (1-12)"),
        year: z.number().int().min(2000).max(2100).describe("Statement year, e.g. 2026"),
      },
    },
    async ({ month, year }) => {
      const result = await backend("POST", "/api/statements/sync", {
        query: { month, year },
      });
      return { content: [{ type: "text", text: JSON.stringify(result) }] };
    }
  );

  server.registerTool(
    "get_dashboard",
    {
      title: "Get Dashboard",
      description:
        "Return the consolidated expense dashboard for a month: total spend, per-card breakdown, " +
        "per-category totals, top merchants, and upcoming due dates.",
      inputSchema: {
        month: z.number().int().min(1).max(12).describe("Month (1-12)"),
        year: z.number().int().min(2000).max(2100).describe("Year, e.g. 2026"),
      },
    },
    async ({ month, year }) => {
      const result = await backend("GET", "/api/dashboard", {
        query: { month, year },
      });
      return { content: [{ type: "text", text: JSON.stringify(result) }] };
    }
  );

  server.registerTool(
    "list_cards",
    {
      title: "List Cards",
      description: "List all registered cards with their bank, label, and last4.",
      inputSchema: {},
    },
    async () => {
      const result = await backend("GET", "/api/cards");
      return { content: [{ type: "text", text: JSON.stringify(result) }] };
    }
  );

  return server;
}

// --- Expose over Streamable HTTP (stateless: new transport per request) ---
const app = express();
app.use(express.json({ limit: "1mb" }));

app.post("/mcp", async (req, res) => {
  try {
    const server = buildServer();
    const transport = new StreamableHTTPServerTransport({
      sessionIdGenerator: undefined, // stateless
    });
    res.on("close", () => {
      transport.close();
      server.close();
    });
    await server.connect(transport);
    await transport.handleRequest(req, res, req.body);
  } catch (err) {
    console.error("MCP request error:", err.message);
    if (!res.headersSent) {
      res.status(500).json({
        jsonrpc: "2.0",
        error: { code: -32603, message: "Internal server error" },
        id: null,
      });
    }
  }
});

app.get("/health", (_req, res) => res.json({ status: "ok" }));

app.listen(PORT, () => {
  console.log(`CardLens MCP server listening on :${PORT} (backend: ${BACKEND_URL})`);
});
