package com.rana.cardlens.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Body of POST /api/statements — the fields the Claude app extracts from the
 * decrypted statement text and sends back to be validated and stored. Keys
 * match the statement-extraction-contract, plus card_id/month/year so the
 * backend knows which card and period to persist under.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaveStatementRequest {

    @JsonProperty("card_id")
    public Integer cardId;

    @JsonProperty("month")
    public Integer month;

    @JsonProperty("year")
    public Integer year;

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

    @JsonProperty("total_spends")
    public BigDecimal totalSpends;   // this cycle's spend total, for reconciliation

    @JsonProperty("transactions")
    public List<ExtractedStatement.ExtractedTxn> transactions;
}
