package com.rana.cardlens.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "statements",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"card_id", "statement_month", "statement_year"}))
public class Statement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "card_id")
    private Integer cardId;

    @Column(name = "statement_month", nullable = false)
    private Integer statementMonth;

    @Column(name = "statement_year", nullable = false)
    private Integer statementYear;

    @Column(name = "statement_date")
    private LocalDate statementDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "total_due")
    private BigDecimal totalDue;

    @Column(name = "min_due")
    private BigDecimal minDue;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "OK";   // OK | NEEDS_REVIEW

    @Column(name = "processed_at")
    private Instant processedAt = Instant.now();

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getCardId() { return cardId; }
    public void setCardId(Integer cardId) { this.cardId = cardId; }

    public Integer getStatementMonth() { return statementMonth; }
    public void setStatementMonth(Integer statementMonth) { this.statementMonth = statementMonth; }

    public Integer getStatementYear() { return statementYear; }
    public void setStatementYear(Integer statementYear) { this.statementYear = statementYear; }

    public LocalDate getStatementDate() { return statementDate; }
    public void setStatementDate(LocalDate statementDate) { this.statementDate = statementDate; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public BigDecimal getTotalDue() { return totalDue; }
    public void setTotalDue(BigDecimal totalDue) { this.totalDue = totalDue; }

    public BigDecimal getMinDue() { return minDue; }
    public void setMinDue(BigDecimal minDue) { this.minDue = minDue; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
