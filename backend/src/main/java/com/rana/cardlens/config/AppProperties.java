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
    private final Claude claude = new Claude();
    private final Gmail gmail = new Gmail();

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public Claude getClaude() { return claude; }
    public Gmail getGmail() { return gmail; }

    public static class Claude {
        private String apiKey;                      // ANTHROPIC_API_KEY
        private String model = "claude-haiku-4-5-20251001";
        private String baseUrl = "https://api.anthropic.com/v1/messages";
        private String version = "2023-06-01";
        private int maxTokens = 4096;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
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
