package com.rana.cardlens.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "statement_id")
    private Integer statementId;

    @Column(name = "txn_date")
    private LocalDate txnDate;

    @Column(name = "merchant", length = 200)
    private String merchant;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "category", length = 50)
    private String category;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getStatementId() { return statementId; }
    public void setStatementId(Integer statementId) { this.statementId = statementId; }

    public LocalDate getTxnDate() { return txnDate; }
    public void setTxnDate(LocalDate txnDate) { this.txnDate = txnDate; }

    public String getMerchant() { return merchant; }
    public void setMerchant(String merchant) { this.merchant = merchant; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
