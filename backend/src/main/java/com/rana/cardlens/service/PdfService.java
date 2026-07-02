package com.rana.cardlens.service;

import com.rana.cardlens.model.Card;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Decrypts a password-protected statement PDF entirely in memory.
 * The password itself is derived from a per-card ENV VAR (its NAME is stored
 * on the card record as password_template_key). No formula lives in code/DB.
 */
@Service
public class PdfService {

    private final Environment env;

    public PdfService(Environment env) {
        this.env = env;
    }

    /**
     * Resolves the per-card password from its env var, decrypts the PDF, and
     * returns the extracted text. Bytes never touch disk. The PDDocument is
     * closed (and thus discarded) before returning.
     */
    public String decryptAndExtractText(byte[] pdfBytes, Card card) throws Exception {
        String password = resolvePassword(card);
        try (PDDocument doc = Loader.loadPDF(pdfBytes, password)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    /**
     * Some flows send the decrypted PDF straight to Claude. This returns the
     * decrypted (unencrypted) bytes in memory for that path.
     */
    public byte[] decryptToBytes(byte[] pdfBytes, Card card) throws Exception {
        String password = resolvePassword(card);
        try (PDDocument doc = Loader.loadPDF(pdfBytes, password)) {
            doc.setAllSecurityToBeRemoved(true);
            var out = new java.io.ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /**
     * Extracts text from ALREADY-decrypted PDF bytes (no password needed).
     * Used only to cheaply check which card a statement belongs to (its last4
     * is printed on the statement) before the paid Claude call. Text is never
     * logged and bytes never touch disk.
     */
    public String extractText(byte[] decryptedPdfBytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(decryptedPdfBytes)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private String resolvePassword(Card card) {
        String key = card.getPasswordTemplateKey();
        String password = env.getProperty(key);
        if (password == null || password.isBlank()) {
            // Never echo the value; only the missing key name.
            throw new IllegalStateException(
                    "Password env var not set for key: " + key);
        }
        return password;
    }
}
