package com.rana.cardlens.service;

import com.rana.cardlens.model.Card;
import com.rana.cardlens.model.ExtractedStatement;
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

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the monthly sync: for each card, fetch email -> decrypt PDF ->
 * extract via Claude -> validate -> persist. Idempotent per (card, month, year).
 * PDF bytes exist only as local variables and are dropped when the method returns.
 */
@Service
public class StatementService {

    private static final Logger log = LoggerFactory.getLogger(StatementService.class);

    private final CardRepository cardRepo;
    private final StatementRepository statementRepo;
    private final TransactionRepository txnRepo;
    private final GmailService gmailService;
    private final PdfService pdfService;
    private final ExtractionService extractionService;

    public StatementService(CardRepository cardRepo,
                            StatementRepository statementRepo,
                            TransactionRepository txnRepo,
                            GmailService gmailService,
                            PdfService pdfService,
                            ExtractionService extractionService) {
        this.cardRepo = cardRepo;
        this.statementRepo = statementRepo;
        this.txnRepo = txnRepo;
        this.gmailService = gmailService;
        this.pdfService = pdfService;
        this.extractionService = extractionService;
    }

    public record SyncResult(String card, String status) {}

    /**
     * Outcome of a sync request. status is "OK" when the period was valid and
     * the pipeline ran (see {@code results} per card), or "REJECTED" with a
     * human-readable {@code reason} when the request was refused up front (e.g.
     * a future period). Claude relays {@code reason} to the user verbatim.
     */
    public record SyncResponse(String status, String reason, List<SyncResult> results) {
        static SyncResponse ok(List<SyncResult> results) {
            return new SyncResponse("OK", null, results);
        }
        static SyncResponse rejected(String reason) {
            return new SyncResponse("REJECTED", reason, List.of());
        }
    }

    @Transactional
    public SyncResponse sync(int month, int year) {
        // Guard BEFORE any Gmail/Claude work. Only the current year is in scope:
        // a valid period is the current month or a past month OF THE CURRENT
        // YEAR. Reject invalid months, earlier years, and future periods up
        // front by comparing against the server's real-world date.
        if (month < 1 || month > 12) {
            return SyncResponse.rejected("Invalid month " + month + "; expected 1-12.");
        }
        YearMonth now = YearMonth.now();
        YearMonth requested = YearMonth.of(year, month);
        if (year < now.getYear()) {
            return SyncResponse.rejected(
                    "Requested period " + requested + " is before the current year; "
                    + "only statements from the current year (" + now.getYear() + ") can be fetched.");
        }
        if (requested.isAfter(now)) {
            return SyncResponse.rejected(
                    "Requested period " + requested + " is in the future; its "
                    + "statements have not been generated yet.");
        }

        List<SyncResult> results = new ArrayList<>();
        for (Card card : cardRepo.findAll()) {
            results.add(syncCard(card, month, year));
        }
        return SyncResponse.ok(results);
    }

    private SyncResult syncCard(Card card, int month, int year) {
        // Idempotency: skip if already processed for this period.
        var existing = statementRepo.findByCardIdAndStatementMonthAndStatementYear(
                card.getId(), month, year);
        if (existing.isPresent() && "OK".equals(existing.get().getStatus())) {
            return new SyncResult(card.getCardLabel(), "SKIPPED (already processed)");
        }

        try {
            List<byte[]> candidates = gmailService.fetchStatementPdfs(card, month, year);
            if (candidates.isEmpty()) {
                return new SyncResult(card.getCardLabel(), "NO_EMAIL");
            }

            // Disambiguate when several cards share a sender. Two cheap filters
            // run before the (paid) Claude call:
            //   1. decrypt with THIS card's password — a statement for another
            //      card is dropped here unless it shares the exact password;
            //   2. the decrypted text must contain THIS card's last4 — reliably
            //      printed on the statement, so it orders the right one first.
            List<byte[]> last4Match = new ArrayList<>();
            List<byte[]> others = new ArrayList<>();
            for (byte[] encrypted : candidates) {
                byte[] decrypted;
                try {
                    decrypted = pdfService.decryptToBytes(encrypted, card);
                } catch (InvalidPasswordException e) {
                    // This PDF doesn't open with THIS card's password, so it
                    // belongs to another card sharing the sender — skip it.
                    // A MISSING password env var (IllegalStateException) is NOT
                    // caught here: it propagates to the outer handler and
                    // surfaces as a clear ERROR instead of a silent NO_EMAIL.
                    continue;
                }
                if (pdfService.extractText(decrypted).contains(card.getLast4())) {
                    last4Match.add(decrypted);
                } else {
                    others.add(decrypted);
                }
            }
            // Try last4-matching statements first, then the rest as a fallback.
            List<byte[]> ordered = new ArrayList<>(last4Match);
            ordered.addAll(others);
            if (ordered.isEmpty()) {
                return new SyncResult(card.getCardLabel(), "NO_EMAIL");
            }

            ExtractionService.Result review = null;
            for (byte[] decrypted : ordered) {
                ExtractionService.Result result =
                        extractionService.extract(decrypted, card.getLast4(), month, year);
                if (result.valid()) {
                    persistValid(card, month, year, result.data());
                    return new SyncResult(card.getCardLabel(), "OK");
                }
                // A last4 mismatch just means this PDF belongs to another card
                // sharing the sender — keep looking. Any other failure is a
                // genuine review candidate for THIS card.
                boolean differentCard = result.data() != null
                        && result.data().cardLast4 != null
                        && !card.getLast4().equals(result.data().cardLast4);
                if (!differentCard) {
                    review = result;
                }
            }

            if (review != null) {
                persistNeedsReview(card, month, year, review.data());
                log.warn("Statement for card {} flagged NEEDS_REVIEW: {}",
                        card.getLast4(), review.reason());
                return new SyncResult(card.getCardLabel(), "NEEDS_REVIEW: " + review.reason());
            }
            // Every candidate belonged to a different card.
            return new SyncResult(card.getCardLabel(), "NO_EMAIL");

        } catch (Exception e) {
            log.error("Sync failed for card {}: {}", card.getLast4(), e.getMessage());
            return new SyncResult(card.getCardLabel(), "ERROR: " + e.getMessage());
        }
    }

    private void persistValid(Card card, int month, int year, ExtractedStatement d) {
        Statement stmt = upsertStatement(card, month, year, d, "OK");
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
        // Persist a marker row only; never save unvalidated transaction numbers.
        upsertStatement(card, month, year, d, "NEEDS_REVIEW");
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
