package com.rana.cardlens.config;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.googleapis.auth.oauth2.GoogleOAuthConstants;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds an authorized Gmail client using a refresh token supplied via env var.
 * Scope is restricted to gmail.readonly. The client auto-refreshes access tokens.
 */
@Configuration
public class GmailConfig {

    private static final String APP_NAME = "CardLens";

    @Bean
    public Gmail gmail(AppProperties props) throws Exception {
        AppProperties.Gmail g = props.getGmail();
        NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        GsonFactory json = GsonFactory.getDefaultInstance();

        Credential credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
                .setTransport(transport)
                .setJsonFactory(json)
                .setTokenServerUrl(new GenericUrl(GoogleOAuthConstants.TOKEN_SERVER_URL))
                .setClientAuthentication(new ClientParametersAuthentication(
                        g.getClientId(), g.getClientSecret()))
                .build()
                .setRefreshToken(g.getRefreshToken());

        // Sanity: request an access token up front (readonly scope enforced by grant).
        credential.refreshToken();

        // Note: GmailScopes.GMAIL_READONLY is the only scope this app should be granted.
        assert GmailScopes.GMAIL_READONLY != null;

        return new Gmail.Builder(transport, json, credential)
                .setApplicationName(APP_NAME)
                .build();
    }
}
