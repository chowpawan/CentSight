package com.centsight.security;

import com.centsight.domain.User;
import com.centsight.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * After Google signs someone in, upsert them and hand the SPA a JWT.
 * The token goes back in the URL fragment so it never lands in server logs or the Referer header.
 */
@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository users;
    private final JwtService jwt;
    private final String appUrl;
    private final List<String> allowedEmails;

    public OAuth2SuccessHandler(UserRepository users,
                                JwtService jwt,
                                @Value("${centsight.app-url:}") String appUrl,
                                @Value("${centsight.allowed-emails:}") String allowedEmails) {
        this.users = users;
        this.jwt = jwt;
        this.appUrl = appUrl;
        this.allowedEmails = allowedEmails.isBlank() ? List.of()
                : List.of(allowedEmails.toLowerCase(Locale.ROOT).split("\\s*,\\s*"));
    }

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res, Authentication auth)
            throws IOException {

        OAuth2User principal = (OAuth2User) auth.getPrincipal();
        String sub = principal.getAttribute("sub");
        String email = principal.getAttribute("email");

        if (sub == null || email == null) {
            redirectWithError(req, res, "Google did not return an email address");
            return;
        }

        // Optional guest list: when set, only these addresses can get in.
        if (!allowedEmails.isEmpty() && !allowedEmails.contains(email.toLowerCase(Locale.ROOT))) {
            redirectWithError(req, res, "This CentSight instance is invite-only");
            return;
        }

        User user = users.findByGoogleSub(sub).orElseGet(() -> new User(
                sub, email, principal.getAttribute("name"), principal.getAttribute("picture")));
        user.setEmail(email);
        user.setName(principal.getAttribute("name"));
        user.setPictureUrl(principal.getAttribute("picture"));
        user.setLastLogin(Instant.now());
        users.save(user);

        String token = jwt.issue(user.getId(), user.getEmail());
        res.sendRedirect(baseUrl(req) + "/#token=" + URLEncoder.encode(token, StandardCharsets.UTF_8));
    }

    private void redirectWithError(HttpServletRequest req, HttpServletResponse res, String message) throws IOException {
        res.sendRedirect(baseUrl(req) + "/#error=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }

    /**
     * Where to send the browser back to. When the app and API share an origin (the packaged
     * container), that is simply this request's own origin, which also keeps Terraform free of a
     * circular dependency on the service URL. app-url overrides it when the SPA is hosted elsewhere.
     */
    private String baseUrl(HttpServletRequest req) {
        if (!appUrl.isBlank()) return appUrl;
        return ServletUriComponentsBuilder.fromRequestUri(req).replacePath(null).build().toUriString();
    }
}
