---
name: security-guardrails
description: Non-negotiable security rules for CardLens. Use before writing, committing, or reviewing ANY code, configuration, or documentation in this repository.
---

# CardLens Security Guardrails

## Absolute rules
1. NEVER hardcode secrets: no API keys, OAuth client secrets, refresh tokens,
   passwords, or password formulas anywhere in code, config, tests, comments, or docs.
   Reference environment variable NAMES only.
2. NEVER store or log full card numbers, PAN, DOB, or the owner's full name.
   The database holds `last4` only.
3. `.gitignore` must include `.env`, `*.pdf`, and any token/credential files from the
   very first commit.
4. `.env.example` lists variable names with empty/dummy values only.
5. Gmail OAuth scope is `gmail.readonly` — nothing broader.
6. Statement PDFs live in memory only and are discarded after extraction.
7. Every REST endpoint checks the static API key header; the key comes from an env var.
8. All external calls (Gmail, Claude API, DB in production) go over HTTPS/TLS.

## Pre-commit self-check (run mentally on every diff)
- Any string that looks like a secret, card number (>4 consecutive digits of a PAN),
  DOB, or password formula? → remove, move to env var.
- Any `*.pdf` or `.env` file staged? → unstage, gitignore.
- Any log statement printing email bodies, PDF content, or passwords? → remove.
