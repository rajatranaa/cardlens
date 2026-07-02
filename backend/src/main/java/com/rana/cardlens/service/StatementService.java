package com.rana.cardlens.service;

import com.rana.cardlens.model.Card;
import com.rana.cardlens.model.ExtractedStatement;
import com.rana.cardlens.model.Statement;
import com.rana.cardlens.model.Transaction;
import com.rana.cardlens.repository.CardRepository;
import com.rana.cardlens.repository.StatementRepository;
import com.rana.cardlens.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

    @Transactional
    public List<SyncResult> sync(int month, int year) {
        List<SyncResult> results = new ArrayList<>();
        for (Card card : cardRepo.findAll()) {
            results.add(syncCard(card, month, year));
        }
        return results;
    }

    private SyncResult syncCard(Card card, int month, int year) {
        // Idempotency: skip if already processed for this period.
        var existing = statementRepo.findByCardIdAndStatementMonthAndStatementYear(
                card.getId(), month, year);
        if (existing.isPresent() && "OK".equals(existing.get().getStatus())) {
            return new SyncResult(card.getCardLabel(), "SKIPPED (already processed)");
        }

        try {
            byte[] encrypted = gmailService.fetchStatementPdf(card, month, year);
            if (encrypted == null) {
                return new SyncResult(card.getCardLabel(), "NO_EMAIL");
            }

            byte[] decrypted = pdfService.decryptToBytes(encrypted, card);

            ExtractionService.Result result =
                    extractionService.extract(decrypted, card.getLast4(), month, year);

            if (!result.valid()) {
                persistNeedsReview(card, month, year, result.data());
                log.warn("Statement for card {} flagged NEEDS_REVIEW: {}",
                        card.getLast4(), result.reason());
                return new SyncResult(card.getCardLabel(), "NEEDS_REVIEW: " + result.reason());
            }

            persistValid(card, month, year, result.data());
            return new SyncResult(card.getCardLabel(), "OK");

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
