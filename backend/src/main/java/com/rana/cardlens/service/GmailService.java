package com.rana.cardlens.service;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.rana.cardlens.config.AppProperties;
import com.rana.cardlens.model.Card;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Finds the statement email for a card in a given month and downloads the
 * PDF attachment as an in-memory byte[]. Never logs email bodies or content.
 */
@Service
public class GmailService {

    private static final Logger log = LoggerFactory.getLogger(GmailService.class);
    private static final DateTimeFormatter GMAIL_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final Gmail gmail;
    private final String user;

    public GmailService(Gmail gmail, AppProperties props) {
        this.gmail = gmail;
        this.user = props.getGmail().getUser();
    }

    /** Returns the first matching PDF attachment for the card+period, or null. */
    public byte[] fetchStatementPdf(Card card, int month, int year) throws Exception {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate after = ym.atDay(1);
        LocalDate before = ym.plusMonths(1).atDay(1);

        StringBuilder q = new StringBuilder();
        q.append("from:").append(card.getSenderEmail())
         .append(" has:attachment filename:pdf")
         .append(" after:").append(after.format(GMAIL_DATE))
         .append(" before:").append(before.format(GMAIL_DATE));
        if (card.getSubjectPattern() != null && !card.getSubjectPattern().isBlank()) {
            q.append(" subject:(").append(card.getSubjectPattern()).append(")");
        }

        var listResp = gmail.users().messages().list(user).setQ(q.toString()).execute();
        List<Message> messages = listResp.getMessages();
        if (messages == null || messages.isEmpty()) {
            log.info("No statement email found for card {} in {}/{}", card.getLast4(), month, year);
            return null;
        }

        // Newest first is Gmail's default order; take the first with a PDF attachment.
        for (Message m : messages) {
            Message full = gmail.users().messages().get(user, m.getId()).setFormat("full").execute();
            byte[] pdf = findPdfAttachment(full.getPayload(), m.getId());
            if (pdf != null) return pdf;
        }
        return null;
    }

    private byte[] findPdfAttachment(MessagePart part, String messageId) throws Exception {
        if (part == null) return null;

        boolean isPdf = "application/pdf".equalsIgnoreCase(part.getMimeType())
                || (part.getFilename() != null && part.getFilename().toLowerCase().endsWith(".pdf"));

        if (isPdf) {
            MessagePartBody body = part.getBody();
            if (body != null && body.getAttachmentId() != null) {
                MessagePartBody att = gmail.users().messages().attachments()
                        .get(user, messageId, body.getAttachmentId()).execute();
                return Base64.getUrlDecoder().decode(att.getData());
            }
            if (body != null && body.getData() != null) {
                return Base64.getUrlDecoder().decode(body.getData());
            }
        }

        if (part.getParts() != null) {
            List<MessagePart> parts = new ArrayList<>(part.getParts());
            for (MessagePart child : parts) {
                byte[] found = findPdfAttachment(child, messageId);
                if (found != null) return found;
            }
        }
        return null;
    }
}
