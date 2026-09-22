package com.centsight.config;

import com.plaid.client.ApiClient;
import com.plaid.client.request.PlaidApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class PlaidConfig {

    @Bean
    PlaidApi plaidApi(@Value("${centsight.plaid.client-id}") String clientId,
                      @Value("${centsight.plaid.secret}") String secret,
                      @Value("${centsight.plaid.env:sandbox}") String env) {

        Map<String, String> keys = new HashMap<>();
        keys.put("clientId", clientId);
        keys.put("secret", secret);

        ApiClient client = new ApiClient(keys);
        client.setPlaidAdapter(switch (env.toLowerCase()) {
            case "production" -> ApiClient.Production;
            case "sandbox" -> ApiClient.Sandbox;
            default -> throw new IllegalStateException("Unknown PLAID_ENV: " + env);
        });
        return client.createService(PlaidApi.class);
    }
}
