---
name: statement-extraction-contract
description: The strict JSON contract and validation rules for extracting credit card statement data via the Claude API. Use when writing or modifying ExtractionService, its prompts, or its tests.
---

# Statement Extraction Contract

## Request
- Send the decrypted PDF as a base64 `document` content block to POST /v1/messages,
  plus one text instruction.
- The instruction must demand: "Return ONLY valid JSON. No markdown fences, no prose."

## Response JSON schema (exact keys)
{
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

## Validation before persisting (all must pass)
1. Strip accidental ``` fences, then parse with Jackson; parse failure → NEEDS_REVIEW.
2. All dates parseable; statement_date falls in the requested month/year.
3. sum(positive transaction amounts) must equal total_due within ±1% or ₹10
   (whichever is larger); mismatch → NEEDS_REVIEW.
4. card_last4 must match the card record being processed.
