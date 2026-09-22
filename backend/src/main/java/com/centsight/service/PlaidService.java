package com.centsight.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plaid.client.request.PlaidApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;

/** Thin wrapper that runs a Plaid Retrofit call and turns a failure into a PlaidException. */
@Service
public class PlaidService {

    private static final Logger log = LoggerFactory.getLogger(PlaidService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PlaidApi plaid;

    public PlaidService(PlaidApi plaid) {
        this.plaid = plaid;
    }

    public PlaidApi api() {
        return plaid;
    }

    public <T> T call(Call<T> call) {
        Response<T> response;
        try {
            response = call.execute();
        } catch (IOException e) {
            throw new PlaidException("NETWORK_ERROR", "Could not reach Plaid: " + e.getMessage(), 502);
        }

        if (response.isSuccessful() && response.body() != null) {
            return response.body();
        }

        String code = "PLAID_ERROR";
        String message = "Plaid request failed";
        try {
            if (response.errorBody() != null) {
                JsonNode body = MAPPER.readTree(response.errorBody().string());
                code = body.path("error_code").asText("PLAID_ERROR");
                message = body.path("error_message").asText(message);
            }
        } catch (IOException e) {
            log.warn("Could not parse Plaid error body", e);
        }
        throw new PlaidException(code, message, response.code());
    }
}
