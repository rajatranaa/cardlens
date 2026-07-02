package com.rana.cardlens.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rana.cardlens.config.AppProperties;
import com.rana.cardlens.model.ExtractedStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;

/**
 * Calls the Claude Messages API with the decrypted PDF as a base64 document
 * block and enforces the extraction contract. Never logs PDF content.
 */
@Service
public class ExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionService.class);

    private static final String INSTRUCTION = """
        You are extracting data from a credit card statement PDF.
        Return ONLY valid JSON. No markdown fences, no prose.
        Use exactly these keys:
        {
          "card_last4": "1234",
          "statement_date": "YYYY-MM-DD",
          "due_date": "YYYY-MM-DD",
          "total_due": 0.00,
          "min_due": 0.00,
          "transactions": [
            { "date": "YYYY-MM-DD", "merchant": "string", "amount": 0.00, "category": "string" }
          ]
        }
        category must be one of: Food, Travel, Shopping, Fuel, Utilities, Entertainment, Health, Bills, Other.
        Spends are positive; payments, refunds and cashback are negative.
        """;

    private final AppProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    public ExtractionService(AppProperties props) {
        this.props = props;
    }

    /** Result carrying the parsed data plus whether validation passed. */
    public record Result(ExtractedStatement data, boolean valid, String reason) {}

    public Result extract(byte[] pdfBytes, String expectedLast4, int month, int year) {
        try {
            String pdfB64 = Base64.getEncoder().encodeToString(pdfBytes);
            String body = buildRequestBody(pdfB64);

            AppProperties.Claude c = props.getClaude();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(c.getBaseUrl()))
                    .header("content-type", "application/json")
                    .header("x-api-key", c.getApiKey())
                    .header("anthropic-version", c.getVersion())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.error("Claude API returned status {}", resp.statusCode());
                return new Result(null, false, "Claude API error: " + resp.statusCode());
            }

            String text = firstTextBlock(resp.body());
            String cleaned = stripFences(text);
            ExtractedStatement data = mapper.readValue(cleaned, ExtractedStatement.class);

            String reason = validate(data, expectedLast4, month, year);
            return new Result(data, reason == null, reason);

        } catch (Exception e) {
            // Never log e with PDF content; message only.
            log.error("Extraction failed: {}", e.getMessage());
            return new Result(null, false, "Extraction exception: " + e.getMessage());
        }
    }

    private String buildRequestBody(String pdfB64) throws Exception {
        AppProperties.Claude c = props.getClaude();
        Map<String, Object> doc = Map.of(
                "type", "document",
                "source", Map.of(
                        "type", "base64",
                        "media_type", "application/pdf",
                        "data", pdfB64));
        Map<String, Object> instr = Map.of("type", "text", "text", INSTRUCTION);
        Map<String, Object> message = Map.of(
                "role", "user",
                "content", java.util.List.of(doc, instr));
        Map<String, Object> payload = Map.of(
                "model", c.getModel(),
                "max_tokens", c.getMaxTokens(),
                "messages", java.util.List.of(message));
        return mapper.writeValueAsString(payload);
    }

    private String firstTextBlock(String responseJson) throws Exception {
        JsonNode root = mapper.readTree(responseJson);
        JsonNode content = root.path("content");
        for (JsonNode block : content) {
            if ("text".equals(block.path("type").asText())) {
                return block.path("text").asText();
            }
        }
        return "";
    }

    /** Strip accidental ``` or ```json fences. */
    private String stripFences(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```(json)?", "").trim();
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3).trim();
        }
        return t;
    }

    /** Returns null if all checks pass, otherwise the failure reason. */
    private String validate(ExtractedStatement d, String expectedLast4, int month, int year) {
        if (d == null) return "null payload";

        // 2. dates parseable + statement_date in requested month/year
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

        // 4. card_last4 must match
        if (d.cardLast4 == null || !d.cardLast4.equals(expectedLast4)) {
            return "card_last4 mismatch";
        }

        // 3. sum(positive amounts) ≈ total_due within ±1% or ₹10 (larger)
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
        BigDecimal diff = sumPositive.subtract(d.totalDue).abs();
        if (diff.compareTo(tolerance) > 0) {
            return "totals mismatch (sum=" + sumPositive + ", total_due=" + d.totalDue + ")";
        }

        return null;
    }
}
