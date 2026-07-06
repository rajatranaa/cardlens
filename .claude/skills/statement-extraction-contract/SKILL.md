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
  "total_spends": 0.00,          // this cycle's total spend, as printed on the statement
  "transactions": [
    { "date": "YYYY-MM-DD", "merchant": "string", "amount": 0.00, "category": "string" }
  ]
}
- category ∈ {Food, Travel, Shopping, Fuel, Utilities, Entertainment, Health, Bills, Other}
- Spends are positive; payments/refunds/cashback are negative.
- total_due = the amount payable for THIS billing cycle. Use the field labelled
  "Total Amount Due" / "Total Payment Due" / "Total Dues" / "Total Amount Payable".
  When a statement shows several totals, do NOT use "Net Outstanding Balance" /
  "Total Outstanding" / "Closing Balance" — those can include EMI/loan principal not
  due this cycle. total_due pairs with the Minimum Amount Due and Payment Due Date.
- total_spends = this cycle's spend only ("Total spends" / "Total Purchases").
  Omit it only if the statement truly doesn't show one.
- Only extract cards whose fetch status is "OK"; skip NO_EMAIL / ERROR / SKIPPED.

## Validation before persisting (backend, inside save_statement — all must pass)
1. All dates parseable; statement_date falls in the requested month/year.
2. card_last4 must match the card record being saved.
3. If total_spends is present: sum(positive transaction amounts) must equal
   total_spends within ±1% or ₹10 (whichever is larger). Reconcile against
   total_spends, NOT total_due (total_due nets carried balance + payments).
   Skipped when total_spends is absent.
Any failure → the statement is stored as NEEDS_REVIEW (a marker row only; never
unvalidated transaction numbers).
