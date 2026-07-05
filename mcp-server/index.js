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
        "Fetch and decrypt every registered card's statement for a month/year and return it as " +
        "Markdown per card (transaction tables included). This stores nothing and uses no LLM. " +
        "For each returned card whose status is 'OK', read its `markdown`, extract the fields, and call " +
        "`save_statement` once for that card: statement_date (YYYY-MM-DD), due_date (YYYY-MM-DD), " +
        "total_due, min_due, card_last4, and transactions[] {date (YYYY-MM-DD), merchant, amount, category}. " +
        "category must be one of: Food, Travel, Shopping, Fuel, Utilities, Entertainment, Health, Bills, Other. " +
        "Spends are positive; payments, refunds and cashback are negative. Skip cards whose status is not 'OK'. " +
        "After saving each card, call get_dashboard to show the summary. If the response status is 'REJECTED', " +
        "just tell the user the `reason` (e.g. the month is in the future).",
      inputSchema: {
        month: z.number().int().min(1).max(12).describe("Statement month (1-12)"),
        year: z.number().int().min(2000).max(2100).describe("Statement year, e.g. 2026"),
      },
    },
    async ({ month, year }) => {
      const result = await backend("POST", "/api/statements/fetch", {
        query: { month, year },
      });
      return { content: [{ type: "text", text: JSON.stringify(result) }] };
    }
  );

  server.registerTool(
    "save_statement",
    {
      title: "Save Statement",
      description:
        "Persist ONE card's statement fields that you extracted from the text returned by " +
        "fetch_statements. The backend validates the dates, that card_last4 matches the card, and that " +
        "the sum of positive transactions reconciles with total_due before storing; otherwise it stores " +
        "the statement as NEEDS_REVIEW. Call once per card, using the card_id and month/year from the " +
        "fetch_statements result.",
      inputSchema: {
        card_id: z.number().int().describe("cardId from the fetch_statements result"),
        month: z.number().int().min(1).max(12),
        year: z.number().int().min(2000).max(2100),
        card_last4: z.string().describe("last 4 digits as printed on the statement"),
        statement_date: z.string().describe("YYYY-MM-DD"),
        due_date: z.string().describe("YYYY-MM-DD"),
        total_due: z.number(),
        min_due: z.number(),
        transactions: z
          .array(
            z.object({
              date: z.string().describe("YYYY-MM-DD"),
              merchant: z.string(),
              amount: z.number().describe("positive = spend; negative = payment/refund/cashback"),
              category: z.string().describe(
                "Food|Travel|Shopping|Fuel|Utilities|Entertainment|Health|Bills|Other"
              ),
            })
          )
          .describe("every transaction on the statement"),
      },
    },
    async (args) => {
      const result = await backend("POST", "/api/statements", { body: args });
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
