package com.rana.cardlens.service;

import com.rana.cardlens.config.AppProperties;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Converts a decrypted statement PDF to Markdown by piping the bytes to a small
 * Python wrapper around Microsoft markitdown (scripts/pdf_to_markdown.py). The
 * PDF streams in via the process's stdin and the Markdown is read from stdout,
 * so the decrypted PDF never touches disk. Requires Python + markitdown[pdf] on
 * the host (see backend/scripts/requirements.txt).
 */
@Service
public class MarkdownService {

    private final AppProperties.Markdown cfg;

    public MarkdownService(AppProperties props) {
        this.cfg = props.getMarkdown();
    }

    public String toMarkdown(byte[] decryptedPdf) throws Exception {
        Process proc = new ProcessBuilder(cfg.getPython(), cfg.getScript()).start();

        // Drain stderr on a daemon thread to avoid a pipe-buffer deadlock.
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        Thread errThread = new Thread(() -> {
            try { proc.getErrorStream().transferTo(errBuf); } catch (Exception ignored) { }
        });
        errThread.setDaemon(true);
        errThread.start();

        // markitdown reads all of stdin before emitting output, so writing the
        // full PDF and closing stdin before reading stdout cannot deadlock.
        try (OutputStream stdin = proc.getOutputStream()) {
            stdin.write(decryptedPdf);
        }
        byte[] out = proc.getInputStream().readAllBytes();

        if (!proc.waitFor(cfg.getTimeoutSeconds(), TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            throw new IllegalStateException(
                    "markdown conversion timed out after " + cfg.getTimeoutSeconds() + "s");
        }
        errThread.join(1000);
        if (proc.exitValue() != 0) {
            String err = errBuf.toString(StandardCharsets.UTF_8).strip();
            throw new IllegalStateException("markitdown failed (exit " + proc.exitValue()
                    + "): " + (err.length() > 300 ? err.substring(err.length() - 300) : err));
        }
        return new String(out, StandardCharsets.UTF_8);
    }
}
