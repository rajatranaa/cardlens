-- CardLens schema (§7). Full card numbers, DOB, name, and password formulas
-- are NEVER stored here — only last4 for display and an env-var key reference.

CREATE TABLE IF NOT EXISTS cards (
  id SERIAL PRIMARY KEY,
  bank_name VARCHAR(50) NOT NULL,
  card_label VARCHAR(50) NOT NULL,           -- e.g. "HDFC Diners"
  last4 CHAR(4) NOT NULL,                     -- last 4 digits only, never full number
  sender_email VARCHAR(100) NOT NULL,         -- statement sender
  subject_pattern VARCHAR(200),               -- to filter the right email
  password_template_key VARCHAR(50) NOT NULL  -- points to an ENV VAR, not the formula itself
);

CREATE TABLE IF NOT EXISTS statements (
  id SERIAL PRIMARY KEY,
  card_id INT REFERENCES cards(id),
  statement_month INT NOT NULL,
  statement_year INT NOT NULL,
  statement_date DATE,
  due_date DATE,
  total_due NUMERIC(12,2),
  min_due NUMERIC(12,2),
  status VARCHAR(20) NOT NULL DEFAULT 'OK',   -- OK | NEEDS_REVIEW
  processed_at TIMESTAMP DEFAULT now(),
  UNIQUE (card_id, statement_month, statement_year)  -- idempotency
);

CREATE TABLE IF NOT EXISTS transactions (
  id SERIAL PRIMARY KEY,
  statement_id INT REFERENCES statements(id),
  txn_date DATE,
  merchant VARCHAR(200),
  amount NUMERIC(12,2),
  category VARCHAR(50)           -- assigned by Claude during extraction
);
