package com.rana.cardlens.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * All values bind from application.yml, which itself references env vars only.
 * No literal secrets ever live in code.
 */
@Component
@ConfigurationProperties(prefix = "cardlens")
public class AppProperties {

    private String apiKey;          // static key guarding REST endpoints
    private final Gmail gmail = new Gmail();
    private final Markdown markdown = new Markdown();

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public Gmail getGmail() { return gmail; }
    public Markdown getMarkdown() { return markdown; }

    /** PDF -> Markdown conversion via the markitdown Python wrapper. */
    public static class Markdown {
        private String python = "python";                    // MARKDOWN_PYTHON
        private String script = "scripts/pdf_to_markdown.py"; // MARKDOWN_SCRIPT (cwd = backend/)
        private int timeoutSeconds = 60;

        public String getPython() { return python; }
        public void setPython(String python) { this.python = python; }
        public String getScript() { return script; }
        public void setScript(String script) { this.script = script; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    public static class Gmail {
        private String clientId;        // GMAIL_CLIENT_ID
        private String clientSecret;    // GMAIL_CLIENT_SECRET
        private String refreshToken;    // GMAIL_REFRESH_TOKEN
        private String user = "me";

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
    }
}
