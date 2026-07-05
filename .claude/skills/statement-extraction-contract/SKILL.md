---
name: statement-extraction-contract
description: The strict JSON contract and validation rules for extracting credit card statement data. Use when writing or modifying the fetch/save statement flow, the MCP tool descriptions, or the backend save-time validation.
---

# Statement Extraction Contract

Extraction is done by the **Claude app**, not by an LLM API in the backend:
- `fetch_statements(month, year)` returns each card's statement as **Markdown**
  (the backend converts the decrypted PDF via markitdown; tables are preserved).
- The Claude app reads each card's `markdown` and extracts the fields below.
- It calls `save_statement` once per card; the **backend validates** before persisting.

There is NO Anthropic/Claude API key anywhere in this project.

## Fields to extract (exact keys — the save_statement payload)
{
  "card_id": 1,                  // from the fetch_statements result
  "month": 6,                    // from the fetch_statements request
  "year": 2026,
  "card_last4": "1234",
  "statement_date": "YYYY-MM-DD",
  "due_date": "YYYY-MM-DD",
  "total_due": 0.00,
  "min_due": 0.00,
  "transactions": [
    { "date": "YYYY-MM-DD", "merchant": "string", "amount": 0.00, "category": "string" }
  ]
}
- category ∈ {Food, Travel, Shopping, Fuel, Utilities, Entertainment, Health, Bills, Other}
- Spends are positive; payments/refunds/cashback are negative.
- Only extract cards whose fetch status is "OK"; skip NO_EMAIL / ERROR / SKIPPED.

## Validation before persisting (backend, inside save_statement — all must pass)
1. All dates parseable; statement_date falls in the requested month/year.
2. card_last4 must match the card record being saved.
3. sum(positive transaction amounts) must equal total_due within ±1% or ₹10
   (whichever is larger).
Any failure → the statement is stored as NEEDS_REVIEW (a marker row only; never
unvalidated transaction numbers).
