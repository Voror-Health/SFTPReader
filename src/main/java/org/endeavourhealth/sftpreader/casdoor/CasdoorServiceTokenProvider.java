package org.endeavourhealth.sftpreader.casdoor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Gets this service's token from Casdoor ({@code POST <casdoor>/api/login/oauth/access_token}) and caches it
 * until shortly before it expires.
 *
 * <ul>
 *     <li>No username configured: OAuth2 {@code client_credentials} grant (token for the Casdoor application).</li>
 *     <li>Username/password configured: {@code password} grant as that Casdoor service user. Use this when the
 *     called service checks user roles (e.g. DataSteward's valve requires {@code vhp_service}), because an
 *     application token from client_credentials does not carry user roles.</li>
 * </ul>
 */
public class CasdoorServiceTokenProvider implements ServiceTokenProvider {

    private static final Logger LOG = LoggerFactory.getLogger(CasdoorServiceTokenProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Refresh this long before the token's stated expiry, so a token never expires mid-request. */
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(60);

    private final HttpClient httpClient;
    private final URI tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final String username;
    private final String password;
    private final Duration timeout;
    private final Clock clock;

    private String accessToken;
    private Instant refreshAfter = Instant.MIN;

    public CasdoorServiceTokenProvider(HttpClient httpClient, String casdoorEndpoint,
                                       String clientId, String clientSecret,
                                       String username, String password,
                                       Duration timeout, Clock clock) {
        this.httpClient = httpClient;
        this.tokenUri = URI.create(stripTrailingSlash(casdoorEndpoint) + "/api/login/oauth/access_token");
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.username = username;
        this.password = password;
        this.timeout = timeout;
        this.clock = clock;
    }

    private boolean usePasswordGrant() {
        return username != null && !username.isBlank();
    }

    @Override
    public synchronized String getAccessToken() throws Exception {
        if (accessToken != null && clock.instant().isBefore(refreshAfter)) {
            return accessToken;
        }

        StringBuilder form = new StringBuilder();
        if (usePasswordGrant()) {
            form.append("grant_type=password")
                    .append("&username=").append(encode(username))
                    .append("&password=").append(encode(password == null ? "" : password));
        } else {
            form.append("grant_type=client_credentials");
        }
        form.append("&client_id=").append(encode(clientId))
                .append("&client_secret=").append(encode(clientSecret));

        HttpRequest request = HttpRequest.newBuilder(tokenUri)
                .timeout(timeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new Exception("Casdoor token request to " + tokenUri + " failed with HTTP " + response.statusCode());
        }

        JsonNode json = MAPPER.readTree(response.body());
        String token = json.path("access_token").asText("");
        if (token.isEmpty()) {
            //Casdoor reports bad credentials as HTTP 200 with an "error" field
            String error = json.path("error").asText("") + " " + json.path("error_description").asText("");
            throw new Exception("Casdoor did not return an access token for " + describe() + ": " + error.trim());
        }

        long expiresIn = json.path("expires_in").asLong(0);
        Duration lifetime = Duration.ofSeconds(expiresIn);
        Instant now = clock.instant();
        //if the token is very short lived (or no expiry given) don't cache it beyond this call
        refreshAfter = lifetime.compareTo(EXPIRY_MARGIN) > 0 ? now.plus(lifetime).minus(EXPIRY_MARGIN) : now;
        accessToken = token;

        LOG.debug("Obtained Casdoor service token for {} (expires in {}s)", describe(), expiresIn);
        return accessToken;
    }

    @Override
    public synchronized void invalidate() {
        accessToken = null;
        refreshAfter = Instant.MIN;
    }

    private String describe() {
        return usePasswordGrant() ? "user " + username + " (client " + clientId + ")" : "client " + clientId;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
