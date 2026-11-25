package org.endeavourhealth.sftpreader.datasteward;

import org.endeavourhealth.sftpreader.casdoor.CasdoorServiceTokenProvider;
import org.endeavourhealth.sftpreader.casdoor.ServiceTokenProvider;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the client against a local stand-in for Casdoor's token endpoint and DataSteward's
 * {@code /machine-api/organisation/hasDPA}.
 */
class DataStewardClientTest {

    private HttpServer server;
    private String baseUrl;
    private MutableClock clock;

    // behaviour of the fake services
    private volatile String dpaBody = "true";
    private volatile int dpaStatus = 200;
    private volatile int rejectTokensUpTo = 0;     // tokens with sequence <= this get a 401
    private volatile String tokenResponse = null;  // override for the Casdoor response body

    private final AtomicInteger tokensIssued = new AtomicInteger();
    private final List<String> dpaQueries = new CopyOnWriteArrayList<>();
    private final List<String> authHeaders = new CopyOnWriteArrayList<>();
    private final List<String> tokenForms = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/login/oauth/access_token", this::handleToken);
        server.createContext("/vhp-datasteward/machine-api/organisation/hasDPA", this::handleHasDpa);
        server.start();
        String host = "http://127.0.0.1:" + server.getAddress().getPort();
        baseUrl = host + "/vhp-datasteward/machine-api";
        clock = new MutableClock(Instant.parse("2026-11-12T10:00:00Z"));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private DataStewardClient client() {
        return client(null, null);
    }

    private DataStewardClient client(String username, String password) {
        HttpClient http = HttpClient.newHttpClient();
        String casdoor = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        ServiceTokenProvider tokens = new CasdoorServiceTokenProvider(
                http, casdoor, "sftpreader", "s3cret&x", username, password, Duration.ofSeconds(5), clock);
        return new DataStewardClient(http, baseUrl + "/", tokens, Duration.ofMinutes(5), Duration.ofSeconds(5), clock);
    }

    @Test
    void usesPasswordGrantWhenServiceUserConfigured() throws Exception {
        DataStewardClient client = client("svc-sftpreader", "p@ss word");

        assertTrue(client.hasDpa("F82001"));

        String form = URLDecoder.decode(tokenForms.get(0), StandardCharsets.UTF_8);
        assertTrue(form.contains("grant_type=password"), form);
        assertTrue(form.contains("username=svc-sftpreader"), form);
        assertTrue(form.contains("password=p@ss word"), form);
        assertTrue(form.contains("client_id=sftpreader"), form);
        assertFalse(form.contains("client_credentials"), form);
    }

    @Test
    void returnsDataStewardAnswerAndSendsServiceToken() throws Exception {
        DataStewardClient client = client();

        assertTrue(client.hasDpa("F82001"));

        assertEquals(List.of("organisationId=F82001"), dpaQueries);
        assertEquals(List.of("Bearer token-1"), authHeaders);
        String form = URLDecoder.decode(tokenForms.get(0), StandardCharsets.UTF_8);
        assertTrue(form.contains("grant_type=client_credentials"), form);
        assertTrue(form.contains("client_id=sftpreader"), form);
        assertTrue(form.contains("client_secret=s3cret&x"), form);

        dpaBody = "false\n";
        client.clearCache();
        assertFalse(client.hasDpa("F82001"));
    }

    @Test
    void normalisesParentChildAndBracedIdentifiers() throws Exception {
        DataStewardClient client = client();

        client.hasDpa("YDDH3_09A");
        client.hasDpa("{ABC12}");

        assertEquals(List.of("organisationId=YDDH3", "organisationId=ABC12"), dpaQueries);
    }

    @Test
    void cachesAnswersUntilTtlExpires() throws Exception {
        DataStewardClient client = client();

        assertTrue(client.hasDpa("F82001"));
        assertTrue(client.hasDpa("F82001"));
        assertTrue(client.hasDpa("F82001_X"));   // same parent, same cache entry
        assertEquals(1, dpaQueries.size());

        clock.advance(Duration.ofMinutes(6));
        assertTrue(client.hasDpa("F82001"));
        assertEquals(2, dpaQueries.size());
        // the token (1 hour lifetime) is still reused
        assertEquals(1, tokensIssued.get());
    }

    @Test
    void retriesOnceWithFreshTokenAfter401() throws Exception {
        rejectTokensUpTo = 1;
        DataStewardClient client = client();

        assertTrue(client.hasDpa("F82001"));

        assertEquals(List.of("Bearer token-1", "Bearer token-2"), authHeaders);
        assertEquals(2, tokensIssued.get());
    }

    @Test
    void errorsAreThrownAndNotCached() throws Exception {
        DataStewardClient client = client();

        dpaStatus = 500;
        dpaBody = "boom";
        Exception ex = assertThrows(Exception.class, () -> client.hasDpa("F82001"));
        assertTrue(ex.getMessage().contains("HTTP 500"), ex.getMessage());

        dpaStatus = 200;
        dpaBody = "true";
        assertTrue(client.hasDpa("F82001"));
        assertEquals(2, dpaQueries.size());
    }

    @Test
    void unexpectedBodyIsAnError() {
        dpaBody = "{\"hasDPA\":true}";
        DataStewardClient client = client();

        Exception ex = assertThrows(Exception.class, () -> client.hasDpa("F82001"));
        assertTrue(ex.getMessage().contains("Unexpected DataSteward DPA response"), ex.getMessage());
    }

    @Test
    void casdoorErrorResponseIsReported() {
        tokenResponse = "{\"error\":\"invalid_client\",\"error_description\":\"client secret is invalid\"}";
        DataStewardClient client = client();

        Exception ex = assertThrows(Exception.class, () -> client.hasDpa("F82001"));
        assertTrue(ex.getMessage().contains("invalid_client"), ex.getMessage());
        assertTrue(dpaQueries.isEmpty());
    }

    @Test
    void blankOrganisationIsRejected() {
        DataStewardClient client = client();
        assertThrows(IllegalArgumentException.class, () -> client.hasDpa(" "));
    }

    // ------------------------------------------------------------------------------------------

    private void handleToken(HttpExchange exchange) throws IOException {
        tokenForms.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        String body = tokenResponse;
        if (body == null) {
            int n = tokensIssued.incrementAndGet();
            body = "{\"access_token\":\"token-" + n + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}";
        }
        respond(exchange, 200, body);
    }

    private void handleHasDpa(HttpExchange exchange) throws IOException {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        authHeaders.add(auth);
        dpaQueries.add(exchange.getRequestURI().getRawQuery());
        int tokenSeq = Integer.parseInt(auth.substring("Bearer token-".length()));
        if (tokenSeq <= rejectTokensUpTo) {
            respond(exchange, 401, "Invalid or expired token");
            return;
        }
        respond(exchange, dpaStatus, dpaBody);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
