---
name: cardlens-conventions
description: CardLens project conventions and architecture rules. Use whenever creating or modifying any Java, SQL, or MCP-server code in this repository.
---

# CardLens Project Conventions

## Stack (fixed — do not substitute)
- Java 17+, Spring Boot 3.x, Spring Web, Spring Data JPA
- Apache PDFBox (PDF decryption), Jackson (JSON), Google API Client (Gmail)
- PostgreSQL; Node.js for the MCP server
- Python 3.10+ with markitdown[pdf] for PDF→Markdown (backend/scripts/pdf_to_markdown.py)

## Package structure
`com.rana.cardlens.{controller, service, model, repository, config}`

## Layer rules
- Controllers are thin: validate params, delegate to services, no business logic.
- Services and their single responsibility:
  - GmailService — OAuth token refresh, per-card email search, attachment download as byte[]
  - PdfService — build password from env-var template + card record, decrypt via PDFBox, extract text
  - MarkdownService — pipe decrypted PDF bytes to the markitdown Python wrapper (stdin/stdout, no disk)
  - StatementService — fetch+decrypt+convert to return statement MARKDOWN per card; validate the fields
    the Claude app extracts (save_statement) and persist; idempotent per (card, month, year)
  - DashboardService — totals, per-card, per-category, due-date summaries
- NO LLM API in the backend. Extraction is done by the Claude app: fetch_statements returns text,
  Claude extracts, save_statement validates + stores. Never add an Anthropic/Claude API key.
- PDFs exist ONLY as in-memory byte[]. Never write PDF bytes to disk, DB, or logs.
- Idempotency: before insert, respect UNIQUE (card_id, statement_month, statement_year); re-sync must not duplicate.
- Failed validation → statement status NEEDS_REVIEW; never persist unvalidated numbers.

## Config
- application.yml contains ${ENV_VAR} references only — never literal values.
