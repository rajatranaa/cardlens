package com.rana.cardlens.service;

import com.rana.cardlens.model.Card;
import com.rana.cardlens.model.ExtractedStatement;
import com.rana.cardlens.model.SaveStatementRequest;
import com.rana.cardlens.model.Statement;
import com.rana.cardlens.model.Transaction;
import com.rana.cardlens.repository.CardRepository;
import com.rana.cardlens.repository.StatementRepository;
import com.rana.cardlens.repository.TransactionRepository;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates the monthly flow with NO LLM API. For each card the backend
 * fetches the statement email, decrypts the password-protected PDF, and returns
 * the decrypted TEXT ({@link #fetch}). The Claude app reads that text, extracts
 * the fields, and calls back into {@link #saveStatement}, which validates and
 * persists. Idempotent per (card, month, year). PDF bytes and text exist only as
 * local variables and are dropped when the method returns.
 */
@Service
public class StatementService {

    private static final Logger log = LoggerFactory.getLogger(StatementService.class);

    private final CardRepository cardRepo;
    private final StatementRepository statementRepo;
    private final TransactionRepository txnRepo;
    private final GmailService gmailService;
    private final PdfService pdfService;
    private final MarkdownService markdownService;

    public StatementService(CardRepository cardRepo,
                            StatementRepository statementRepo,
                            TransactionRepository txnRepo,
                            GmailService gmailService,
                            PdfService pdfService,
                            MarkdownService markdownService) {
        this.cardRepo = cardRepo;
        this.statementRepo = statementRepo;
        this.txnRepo = txnRepo;
        this.gmailService = gmailService;
        this.pdfService = pdfService;
        this.markdownService = markdownService;
    }

    // ---------- fetch: return decrypted statement text per card (no LLM) ----------

    /** Decrypted statement as Markdown (or a status) for one card in the period. */
    public record CardText(Integer cardId, String cardLabel, String last4,
                           String status, String markdown) {}

    /** Outcome of a fetch request. REJECTED for invalid/out-of-range periods. */
    public record FetchResponse(String status, String reason,
                                Integer month, Integer year, List<CardText> cards) {
        static FetchResponse ok(int month, int year, List<CardText> cards) {
            return new FetchResponse("OK", null, month, year, cards);
        }
        static FetchResponse rejected(String reason) {
            return new FetchResponse("REJECTED", reason, null, null, List.of());
        }
    }

    /**
     * Fetch + decrypt each card's statement for the period and return the plain
     * text. Nothing is stored here; the Claude app extracts the fields from the
     * text and calls {@link #saveStatement}.
     */
    public FetchResponse fetch(int month, int year) {
        String rejection = guardPeriod(month, year);
        if (rejection != null) return FetchResponse.rejected(rejection);

        List<CardText> cards = new ArrayList<>();
        for (Card card : cardRepo.findAll()) {
            cards.add(fetchCardText(card, month, year));
        }
        return FetchResponse.ok(month, year, cards);
    }

    private CardText fetchCardText(Card card, int month, int year) {
        // Idempotency: skip cards already stored OK for this period.
        var existing = statementRepo.findByCardIdAndStatementMonthAndStatementYear(
                card.getId(), month, year);
        if (existing.isPresent() && "OK".equals(existing.get().getStatus())) {
            return new CardText(card.getId(), card.getCardLabel(), card.getLast4(),
                    "SKIPPED (already processed)", null);
        }
        try {
            List<byte[]> candidates = gmailService.fetchStatementPdfs(card, month, year);
            if (candidates.isEmpty()) {
                return new CardText(card.getId(), card.getCardLabel(), card.getLast4(), "NO_EMAIL", null);
            }
            // Disambiguate shared-sender inboxes without an LLM: decrypt with THIS
            // card's password (a statement for another card fails here unless it
            // shares the exact password), then prefer the one whose text contains
            // THIS card's last4. PDFBox text extraction is only for this cheap
            // check; the chosen statement is converted to Markdown for Claude.
            byte[] chosen = null;
            byte[] fallback = null;
            for (byte[] encrypted : candidates) {
                byte[] decrypted;
                try {
                    decrypted = pdfService.decryptToBytes(encrypted, card);
                } catch (InvalidPasswordException e) {
                    continue; // not this card's statement
                }
                if (pdfService.extractText(decrypted).contains(card.getLast4())) {
                    chosen = decrypted;
                    break;
                }
                if (fallback == null) fallback = decrypted; // only-decryptable candidate
            }
            if (chosen == null) chosen = fallback;
            if (chosen == null) {
                return new CardText(card.getId(), card.getCardLabel(), card.getLast4(), "NO_EMAIL", null);
            }
            String markdown = markdownService.toMarkdown(chosen);
            return new CardText(card.getId(), card.getCardLabel(), card.getLast4(), "OK", markdown);
        } catch (Exception e) {
            // Never log PDF/email content; message only.
            log.error("Fetch failed for card {}: {}", card.getLast4(), e.getMessage());
            return new CardText(card.getId(), card.getCardLabel(), card.getLast4(),
                    "ERROR: " + e.getMessage(), null);
        }
    }

    // ---------- save: validate the Claude-extracted fields and persist ----------

    public record SaveResult(String card, String status, String reason) {}

    /**
     * Validate the fields the Claude app extracted and persist them. Fails
     * validation -> stored as NEEDS_REVIEW (a marker only; never unvalidated
     * numbers). Idempotent per (card, month, year).
     */
    @Transactional
    public SaveResult saveStatement(SaveStatementRequest req) {
        Optional<Card> cardOpt = req.cardId == null ? Optional.empty() : cardRepo.findById(req.cardId);
        if (cardOpt.isEmpty()) {
            return new SaveResult("card#" + req.cardId, "ERROR", "unknown card_id: " + req.cardId);
        }
        Card card = cardOpt.get();
        if (req.month == null || req.month < 1 || req.month > 12 || req.year == null) {
            return new SaveResult(card.getCardLabel(), "ERROR", "invalid month/year");
        }

        ExtractedStatement d = toExtracted(req);
        String reason = validate(d, card.getLast4(), req.month, req.year);
        if (reason != null) {
            persistNeedsReview(card, req.month, req.year, d);
            log.warn("Statement for card {} flagged NEEDS_REVIEW: {}", card.getLast4(), reason);
            return new SaveResult(card.getCardLabel(), "NEEDS_REVIEW", reason);
        }
        persistValid(card, req.month, req.year, d);
        return new SaveResult(card.getCardLabel(), "OK", null);
    }

    private ExtractedStatement toExtracted(SaveStatementRequest req) {
        ExtractedStatement d = new ExtractedStatement();
        d.cardLast4 = req.cardLast4;
        d.statementDate = req.statementDate;
        d.dueDate = req.dueDate;
        d.totalDue = req.totalDue;
        d.minDue = req.minDue;
        d.transactions = req.transactions;
        return d;
    }

    /** Returns null if all checks pass, otherwise the failure reason. */
    private String validate(ExtractedStatement d, String expectedLast4, int month, int year) {
        if (d == null) return "null payload";

        LocalDate stmt;
        try {
            stmt = LocalDate.parse(d.statementDate);
            LocalDate.parse(d.dueDate);
            if (d.transactions != null) {
                for (var t : d.transactions) LocalDate.parse(t.date);
            }
        } catch (Exception e) {
            return "unparseable date";
        }
        if (stmt.getMonthValue() != month || stmt.getYear() != year) {
            return "statement_date outside requested period";
        }
        if (d.cardLast4 == null || !d.cardLast4.equals(expectedLast4)) {
            return "card_last4 mismatch";
        }
        if (d.totalDue == null) return "missing total_due";

        BigDecimal sumPositive = BigDecimal.ZERO;
        if (d.transactions != null) {
            for (var t : d.transactions) {
                if (t.amount != null && t.amount.signum() > 0) {
                    sumPositive = sumPositive.add(t.amount);
                }
            }
        }
        BigDecimal tolerance = d.totalDue.abs()
                .multiply(new BigDecimal("0.01"))
                .max(new BigDecimal("10"));
        if (sumPositive.subtract(d.totalDue).abs().compareTo(tolerance) > 0) {
            return "totals mismatch (sum=" + sumPositive + ", total_due=" + d.totalDue + ")";
        }
        return null;
    }

    // ---------- shared helpers ----------

    /** Reject invalid months, earlier years, and future periods; null if valid. */
    private String guardPeriod(int month, int year) {
        if (month < 1 || month > 12) {
            return "Invalid month " + month + "; expected 1-12.";
        }
        YearMonth now = YearMonth.now();
        YearMonth requested = YearMonth.of(year, month);
        if (year < now.getYear()) {
            return "Requested period " + requested + " is before the current year; "
                    + "only statements from the current year (" + now.getYear() + ") can be fetched.";
        }
        if (requested.isAfter(now)) {
            return "Requested period " + requested + " is in the future; its "
                    + "statements have not been generated yet.";
        }
        return null;
    }

    private void persistValid(Card card, int month, int year, ExtractedStatement d) {
        Statement stmt = upsertStatement(card, month, year, d, "OK");
        // Replace any prior transactions for this statement (idempotent re-save).
        txnRepo.deleteByStatementId(stmt.getId());
        if (d.transactions != null) {
            for (var t : d.transactions) {
                Transaction txn = new Transaction();
                txn.setStatementId(stmt.getId());
                txn.setTxnDate(LocalDate.parse(t.date));
                txn.setMerchant(t.merchant);
                txn.setAmount(t.amount);
                txn.setCategory(t.category);
                txnRepo.save(txn);
            }
        }
    }

    private void persistNeedsReview(Card card, int month, int year, ExtractedStatement d) {
        // Persist a marker row only; never save unvalidated transaction numbers
        // (and drop any that a prior valid save may have stored for this period).
        Statement stmt = upsertStatement(card, month, year, d, "NEEDS_REVIEW");
        txnRepo.deleteByStatementId(stmt.getId());
    }

    private Statement upsertStatement(Card card, int month, int year,
                                      ExtractedStatement d, String status) {
        Statement stmt = statementRepo
                .findByCardIdAndStatementMonthAndStatementYear(card.getId(), month, year)
                .orElseGet(Statement::new);
        stmt.setCardId(card.getId());
        stmt.setStatementMonth(month);
        stmt.setStatementYear(year);
        stmt.setStatus(status);
        if ("OK".equals(status) && d != null) {
            stmt.setStatementDate(LocalDate.parse(d.statementDate));
            stmt.setDueDate(LocalDate.parse(d.dueDate));
            stmt.setTotalDue(d.totalDue);
            stmt.setMinDue(d.minDue);
        }
        return statementRepo.save(stmt);
    }

    public List<Statement> byPeriod(int month, int year) {
        return statementRepo.findByStatementMonthAndStatementYear(month, year);
    }
}
