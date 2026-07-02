package com.rana.cardlens.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Shape of the JSON returned by Claude per statement-extraction-contract.
 * Keys match the contract exactly.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractedStatement {

    @JsonProperty("card_last4")
    public String cardLast4;

    @JsonProperty("statement_date")
    public String statementDate;   // YYYY-MM-DD

    @JsonProperty("due_date")
    public String dueDate;         // YYYY-MM-DD

    @JsonProperty("total_due")
    public BigDecimal totalDue;

    @JsonProperty("min_due")
    public BigDecimal minDue;

    @JsonProperty("transactions")
    public List<ExtractedTxn> transactions;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtractedTxn {
        @JsonProperty("date")
        public String date;        // YYYY-MM-DD
        @JsonProperty("merchant")
        public String merchant;
        @JsonProperty("amount")
        public BigDecimal amount;
        @JsonProperty("category")
        public String category;
    }
}
