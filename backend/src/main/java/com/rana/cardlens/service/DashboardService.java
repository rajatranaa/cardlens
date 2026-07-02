package com.rana.cardlens.service;

import com.rana.cardlens.model.Card;
import com.rana.cardlens.model.Statement;
import com.rana.cardlens.model.Transaction;
import com.rana.cardlens.repository.CardRepository;
import com.rana.cardlens.repository.StatementRepository;
import com.rana.cardlens.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Read-only aggregation for the consolidated dashboard Claude renders in chat.
 */
@Service
public class DashboardService {

    private final CardRepository cardRepo;
    private final StatementRepository statementRepo;
    private final TransactionRepository txnRepo;

    public DashboardService(CardRepository cardRepo,
                            StatementRepository statementRepo,
                            TransactionRepository txnRepo) {
        this.cardRepo = cardRepo;
        this.statementRepo = statementRepo;
        this.txnRepo = txnRepo;
    }

    public Map<String, Object> build(int month, int year) {
        List<Statement> statements =
                statementRepo.findByStatementMonthAndStatementYear(month, year);

        Map<Integer, Card> cardsById = cardRepo.findAll().stream()
                .collect(Collectors.toMap(Card::getId, c -> c));

        List<Integer> stmtIds = statements.stream()
                .map(Statement::getId).toList();
        List<Transaction> txns = stmtIds.isEmpty()
                ? List.of() : txnRepo.findByStatementIdIn(stmtIds);

        BigDecimal totalSpend = txns.stream()
                .map(Transaction::getAmount)
                .filter(a -> a != null && a.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Per-card breakdown
        Map<Integer, List<Transaction>> byStmt = txns.stream()
                .collect(Collectors.groupingBy(Transaction::getStatementId));
        List<Map<String, Object>> perCard = new ArrayList<>();
        for (Statement s : statements) {
            Card c = cardsById.get(s.getCardId());
            BigDecimal spend = byStmt.getOrDefault(s.getId(), List.of()).stream()
                    .map(Transaction::getAmount)
                    .filter(a -> a != null && a.signum() > 0)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("card", c != null ? c.getCardLabel() : ("card#" + s.getCardId()));
            row.put("last4", c != null ? c.getLast4() : null);
            row.put("spend", spend);
            row.put("total_due", s.getTotalDue());
            row.put("min_due", s.getMinDue());
            row.put("due_date", s.getDueDate());
            row.put("status", s.getStatus());
            perCard.add(row);
        }

        // Per-category
        Map<String, BigDecimal> perCategory = new TreeMap<>();
        for (Transaction t : txns) {
            if (t.getAmount() == null || t.getAmount().signum() <= 0) continue;
            String cat = t.getCategory() == null ? "Other" : t.getCategory();
            perCategory.merge(cat, t.getAmount(), BigDecimal::add);
        }

        // Top merchants
        Map<String, BigDecimal> merchantTotals = new HashMap<>();
        for (Transaction t : txns) {
            if (t.getAmount() == null || t.getAmount().signum() <= 0) continue;
            merchantTotals.merge(
                    t.getMerchant() == null ? "Unknown" : t.getMerchant(),
                    t.getAmount(), BigDecimal::add);
        }
        List<Map<String, Object>> topMerchants = merchantTotals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(10)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("merchant", e.getKey());
                    m.put("amount", e.getValue());
                    return m;
                })
                .collect(Collectors.toList());

        // Upcoming due dates (sorted)
        List<Map<String, Object>> dueDates = statements.stream()
                .filter(s -> s.getDueDate() != null)
                .sorted(Comparator.comparing(Statement::getDueDate))
                .map(s -> {
                    Card c = cardsById.get(s.getCardId());
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("card", c != null ? c.getCardLabel() : ("card#" + s.getCardId()));
                    m.put("due_date", s.getDueDate());
                    m.put("total_due", s.getTotalDue());
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("month", month);
        out.put("year", year);
        out.put("total_spend", totalSpend);
        out.put("statements_found", statements.size());
        out.put("needs_review",
                statements.stream().filter(s -> "NEEDS_REVIEW".equals(s.getStatus())).count());
        out.put("per_card", perCard);
        out.put("per_category", perCategory);
        out.put("top_merchants", topMerchants);
        out.put("due_dates", dueDates);
        out.put("generated_at", LocalDate.now().toString());
        return out;
    }
}
