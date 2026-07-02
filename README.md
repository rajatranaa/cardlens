# CardLens

Automated credit card statement aggregator and expense dashboard. Ask Claude to
"fetch my statements for June" and it fetches the statement emails for all your
cards, decrypts the password-protected PDFs, extracts the transactions, and
renders a consolidated dashboard — all through an MCP connector. No manual email
checking, no separate app to log into.

PDFs are processed **in memory and discarded**. Only extracted transaction data
is persisted. No secrets, card numbers, DOB, or passwords ever touch the repo or
the database (only `last4` for display).

## Architecture

```
Claude app  ──MCP──▶  MCP server (3 tools)  ──REST──▶  Spring Boot backend  ──▶  PostgreSQL
                                                          ├─ Gmail API (fetch statement emails)
                                                          └─ Claude API (PDF → structured JSON)
```

- `backend/` — Spring Boot 3 (Java 17+): Gmail fetch, PDFBox decrypt, Claude extraction, aggregation.
- `mcp-server/` — Node.js MCP server exposing `fetch_statements`, `get_dashboard`, `list_cards`.
- `db/schema.sql` — PostgreSQL schema.
- `.claude/skills/` — build-time guardrails (conventions, security, extraction contract).

## Prerequisites

- Java 17+ and Maven
- Node.js 18+
- A PostgreSQL database (local, or a free tier: Neon / Railway / Render)
- A Google account and an Anthropic API key

## 1. Google Cloud + Gmail OAuth (one-time)

1. Create a project at https://console.cloud.google.com and enable the **Gmail API**.
2. Configure the OAuth consent screen (External, testing mode). Add your own
   Google account as a **test user** — no Google verification is needed for
   personal single-user use.
3. Create **OAuth 2.0 credentials** (Desktop or Web app). Note the client ID and secret.
4. Run a one-time consent flow granting the **`gmail.readonly`** scope only, and
   capture the resulting **refresh token**. Store the client ID, secret, and
   refresh token as environment variables (never in the repo).

## 2. Database

```bash
psql "$DATABASE_URL" -f db/schema.sql
```

Register each card via the API once the backend is running (see below). The
`password_template_key` for each card must be the **name** of an env var that
holds that card's statement password — not the password itself.

## 3. Environment variables

Copy `.env.example` to `.env` and fill in values locally, or set them in your
host's env settings. See that file for the full list (DB, `CARDLENS_API_KEY`,
`ANTHROPIC_API_KEY`, Gmail OAuth, and one password env var per card).

## 4. Run the backend

```bash
cd backend
mvn spring-boot:run
```

Register a card (repeat for all 5):

```bash
curl -X POST http://localhost:8080/api/cards \
  -H "X-Api-Key: $CARDLENS_API_KEY" -H "Content-Type: application/json" \
  -d '{
    "bankName": "HDFC",
    "cardLabel": "HDFC Diners",
    "last4": "1234",
    "senderEmail": "estatement@hdfcbank.net",
    "subjectPattern": "Statement",
    "passwordTemplateKey": "CARD_HDFC_DINERS_PWD"
  }'
```

## 5. Run the MCP server

```bash
cd mcp-server
npm install
BACKEND_URL=http://localhost:8080 CARDLENS_API_KEY=... npm start
```

It listens on `POST /mcp` (Streamable HTTP) on `MCP_PORT` (default 3000).

## 6. Add the connector in Claude

Add the MCP server's URL (e.g. `https://your-deploy.example.com/mcp`) as a custom
connector in Claude. Then in chat:

> Fetch my statements for June 2026.

Claude calls `fetch_statements(month=6, year=2026)`, then `get_dashboard` to show
total spend, per-card breakdown, top merchants, and upcoming due dates.

## REST API

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/statements/sync?month=&year=` | Fetch, decrypt, extract, store for the period |
| `GET`  | `/api/dashboard?month=&year=` | Aggregated view |
| `GET`  | `/api/cards` | List registered cards |
| `POST` | `/api/cards` | Register a card |
| `GET`  | `/api/statements?month=&year=` | Raw statement records |

All `/api/**` endpoints require the `X-Api-Key` header.

## Deployment (free tier)

Deploy the backend and MCP server to Railway or Render (HTTPS is free), and use a
free PostgreSQL tier (Neon / Railway / Render). Set all env vars in the host's
settings. Both services can also run locally on demand.

## Extraction status

If Claude's extracted JSON fails validation (bad dates, `last4` mismatch, or the
transaction total disagrees with `total_due` beyond tolerance), the statement is
saved as `NEEDS_REVIEW` rather than persisting bad numbers.
