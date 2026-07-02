package com.rana.cardlens.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * LOCAL SMOKE-TEST ONLY (profile "local"). Supplies an unauthenticated Gmail
 * client so the Spring context can boot without real OAuth credentials. Any
 * actual Gmail call will fail at request time, which is the expected behaviour
 * for a wiring smoke test (sync degrades to a per-card ERROR). Never active in
 * prod, where the real GmailConfig (@Profile("!local")) is used instead.
 */
@Configuration
@Profile("local")
public class LocalGmailConfig {

    @Bean
    public Gmail gmail() throws Exception {
        NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        GsonFactory json = GsonFactory.getDefaultInstance();
        // No-op request initializer: no credentials attached.
        return new Gmail.Builder(transport, json, request -> {})
                .setApplicationName("CardLens-local")
                .build();
    }
}
