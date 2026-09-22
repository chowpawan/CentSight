package com.centsight.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Sends a failed Google sign-in back to the app with a readable message.
 * Without this, Spring Security falls back to /login?error, which this app does not serve.
 */
@Component
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2FailureHandler.class);

    private final String appUrl;

    public OAuth2FailureHandler(@Value("${centsight.app-url:}") String appUrl) {
        this.appUrl = appUrl;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest req, HttpServletResponse res,
                                        AuthenticationException e) throws IOException {

        String detail = e instanceof OAuth2AuthenticationException oauth
                ? oauth.getError().getErrorCode()
                : e.getMessage();

        // The full reason goes to the log; the browser gets something a person can act on.
        log.warn("Google sign-in failed: {}", detail, e);

        String message = switch (String.valueOf(detail)) {
            case "invalid_client" -> "Sign-in is not configured correctly (invalid Google client).";
            case "access_denied" -> "You declined the Google sign-in.";
            case "authorization_request_not_found" -> "That sign-in link expired. Please try again.";
            default -> "Google sign-in failed. Please try again.";
        };

        String base = appUrl.isBlank()
                ? ServletUriComponentsBuilder.fromRequestUri(req).replacePath(null).build().toUriString()
                : appUrl;

        res.sendRedirect(base + "/#error=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }
}
