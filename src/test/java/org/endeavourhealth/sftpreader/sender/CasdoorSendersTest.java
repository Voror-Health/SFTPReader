package org.endeavourhealth.sftpreader.sender;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.endeavourhealth.core.database.dal.audit.models.HeaderKeys;
import org.endeavourhealth.sftpreader.casdoor.CasdoorServiceAuth;
import org.endeavourhealth.sftpreader.casdoor.CasdoorServiceTokenProvider;
import org.endeavourhealth.sftpreader.datasteward.DataStewardClient;
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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The two outbound calls that used KeycloakClient - EdsSender (Messaging API PostMessageAsync) and DpaCheck -
 * now carry the Casdoor service token. Runs against local stand-ins for Casdoor, the Messaging API and
 * DataSteward.
 */
class CasdoorSendersTest {

    private HttpServer server;
    private String host;

    private final AtomicInteger tokensIssued = new AtomicInteger();
    private final List<String> tokenForms = new CopyOnWriteArrayList<>();
    private final List<Map<String, String>> posts = new CopyOnWriteArrayList<>();
    private final List<String> dpaRequests = new CopyOnWriteArrayList<>();
    private volatile int postStatus = 200;
    private volatile String dpaAnswer = "true";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/login/oauth/access_token", ex -> {
            tokenForms.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int n = tokensIssued.incrementAndGet();
            respond(ex, 200, "{\"access_token\":\"tok-" + n + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
        });
        server.createContext("/vhp-api/machine-api/PostMessageAsync", ex -> {
            Map<String, String> h = new ConcurrentHashMap<>();
            ex.getRequestHeaders().forEach((k, v) -> h.put(k.toLowerCase(), v.get(0)));
            h.put("body", new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            posts.add(h);
            respond(ex, postStatus, postStatus == 200 ? "OK" : "nope");
        });
        server.createContext("/vhp-datasteward/machine-api/organisation/hasDPA", ex -> {
            dpaRequests.add(ex.getRequestURI().getRawQuery() + " " + ex.getRequestHeaders().getFirst("Authorization"));
            respond(ex, 200, dpaAnswer);
        });
        server.start();
        host = "http://127.0.0.1:" + server.getAddress().getPort();

        HttpClient http = HttpClient.newHttpClient();
        CasdoorServiceTokenProvider tokens = new CasdoorServiceTokenProvider(http, host, "sftp-client", "sftp-secret",
                "svc-sftpreader", "pw", Duration.ofSeconds(5), Clock.systemUTC());
        CasdoorServiceAuth.initialise(tokens);
        DataStewardClient.initialise(new DataStewardClient(http, host + "/vhp-datasteward/machine-api", tokens,
                Duration.ofMinutes(5), Duration.ofSeconds(5), Clock.systemUTC()));
    }

    @AfterEach
    void stop() {
        DataStewardClient.shutdown();
        CasdoorServiceAuth.shutdown();
        server.stop(0);
    }

    private String edsUrl() {
        return host + "/vhp-api/machine-api/PostMessageAsync";
    }

    @Test
    void postToMessagingApiCarriesCasdoorBearerToken() throws Exception {
        EdsSenderResponse r = EdsSender.notifyEds(edsUrl(), "<xml/>", true, false, 123L, new Date(), null);

        assertEquals(200, r.getHttpStatusCode());
        Map<String, String> h = posts.get(0);
        assertEquals("Bearer tok-1", h.get("authorization"));
        assertEquals("true", h.get(HeaderKeys.IsBulk.toLowerCase()));
        assertEquals("false", h.get(HeaderKeys.HasPatientData.toLowerCase()));
        assertEquals("123", h.get(HeaderKeys.TotalFileSize.toLowerCase()));
        assertEquals("<xml/>", h.get("body"));

        String form = URLDecoder.decode(tokenForms.get(0), StandardCharsets.UTF_8);
        assertTrue(form.contains("grant_type=password"), form);
        assertTrue(form.contains("username=svc-sftpreader"), form);
        assertTrue(form.contains("client_id=sftp-client"), form);
    }

    @Test
    void tokenIsReusedAcrossPosts() throws Exception {
        EdsSender.notifyEds(edsUrl(), "a", false, true, null, null, null);
        EdsSender.notifyEds(edsUrl(), "b", false, true, null, null, null);
        assertEquals(1, tokensIssued.get());
        assertEquals("Bearer tok-1", posts.get(1).get("authorization"));
    }

    @Test
    void unauthorisedResponseFailsAndDropsTheToken() throws Exception {
        postStatus = 401;
        assertThrows(EdsSenderHttpErrorResponseException.class,
                () -> EdsSender.notifyEds(edsUrl(), "a", false, true, null, null, null));

        postStatus = 200;
        EdsSender.notifyEds(edsUrl(), "a", false, true, null, null, null);
        assertEquals("Bearer tok-2", posts.get(1).get("authorization"));
    }

    @Test
    void missingServiceAuthIsAClearError() {
        CasdoorServiceAuth.shutdown();
        Exception ex = assertThrows(Exception.class,
                () -> EdsSender.notifyEds(edsUrl(), "a", false, true, null, null, null));
        assertTrue(ex.getMessage().contains("'datasteward' / 'auth'"), ex.getMessage());
        assertTrue(posts.isEmpty());
    }

    @Test
    void dpaCheckGoesToDataStewardWithTheSameToken() throws Exception {
        assertTrue(DpaCheck.checkForDpa("YDDH3_09A"));
        assertEquals(List.of("organisationId=YDDH3 Bearer tok-1"), dpaRequests);

        dpaAnswer = "false";
        DataStewardClient.getInstance().clearCache();
        assertFalse(DpaCheck.checkForDpa("F82001"));
    }

    @Test
    void dpaCheckWithoutDataStewardIsAnErrorNotFalse() {
        DataStewardClient.shutdown();
        assertThrows(IllegalStateException.class, () -> DpaCheck.checkForDpa("F82001"));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
