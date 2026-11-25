package org.endeavourhealth.sftpreader.datasteward;

import org.endeavourhealth.sftpreader.casdoor.ServiceTokenProvider;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client for the vhp-datasteward API (copied from vhp-messaging-api). The SFTP Reader used to ask the
 * Messaging API ({@code <messaging api>/dsm/hasDPA/<ods>}), which read the old Data Sharing Manager tables;
 * DPA checks now go straight to DataSteward.
 *
 * <p>Calls {@code GET <url>/organisation/hasDPA?organisationId=<id>}, which answers {@code true}/{@code false}
 * as plain text. Results (both answers) are cached for {@link DataStewardConfig#cacheTtl()}. Failures are
 * never cached and are thrown, so a DataSteward outage shows up as an error rather than as "no DPA".</p>
 */
public class DataStewardClient {

    private static final Logger LOG = LoggerFactory.getLogger(DataStewardClient.class);

    private static volatile DataStewardClient instance;

    private final HttpClient httpClient;
    private final String baseUrl;
    private final ServiceTokenProvider tokenProvider;
    private final Duration cacheTtl;
    private final Duration timeout;
    private final Clock clock;
    private final Map<String, CachedAnswer> cache = new ConcurrentHashMap<>();

    private record CachedAnswer(boolean hasDpa, Instant expires) {
    }

    public DataStewardClient(HttpClient httpClient, String baseUrl, ServiceTokenProvider tokenProvider,
                             Duration cacheTtl, Duration timeout, Clock clock) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.tokenProvider = tokenProvider;
        this.cacheTtl = cacheTtl;
        this.timeout = timeout;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------------------------
    // Singleton lifecycle (set up in SftpReaderServices, used from DpaCheck)
    // ------------------------------------------------------------------------------------------

    /**
     * @param tokens the SFTP Reader's Casdoor service token (the same one sent to the Messaging API)
     */
    public static DataStewardClient create(DataStewardConfig config, ServiceTokenProvider tokens) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return new DataStewardClient(http, config.url(), tokens, config.cacheTtl(), config.timeout(), Clock.systemUTC());
    }

    public static void initialise(DataStewardClient client) {
        instance = client;
    }

    public static void shutdown() {
        instance = null;
    }

    public static DataStewardClient getInstance() {
        DataStewardClient client = instance;
        if (client == null) {
            throw new IllegalStateException("DataStewardClient is not initialised - check the 'datasteward' config row"
                    + " for app_id sftpreader (see the startup log)");
        }
        return client;
    }

    // ------------------------------------------------------------------------------------------
    // DPA check
    // ------------------------------------------------------------------------------------------

    /**
     * @param organisationId ODS code (or DataSteward organisation UUID). Parent/child identifiers such as
     *                       {@code YDDH3_09A} are checked against the parent ({@code YDDH3}), as DataSteward does.
     */
    public boolean hasDpa(String organisationId) throws Exception {
        if (organisationId == null || organisationId.isBlank()) {
            throw new IllegalArgumentException("organisationId is required for a DPA check");
        }
        String key = normalise(organisationId);

        CachedAnswer cached = cache.get(key);
        if (cached != null && clock.instant().isBefore(cached.expires())) {
            return cached.hasDpa();
        }

        boolean hasDpa = callHasDpa(key);
        cache.put(key, new CachedAnswer(hasDpa, clock.instant().plus(cacheTtl)));
        LOG.debug("DataSteward DPA check for {} -> {}", key, hasDpa);
        return hasDpa;
    }

    public void evict(String organisationId) {
        if (organisationId != null) {
            cache.remove(normalise(organisationId));
        }
    }

    public void clearCache() {
        cache.clear();
    }

    private boolean callHasDpa(String organisationId) throws Exception {
        URI uri = URI.create(baseUrl + "/organisation/hasDPA?organisationId="
                + URLEncoder.encode(organisationId, StandardCharsets.UTF_8));

        HttpResponse<String> response = send(uri, tokenProvider.getAccessToken());
        if (response.statusCode() == 401) {
            //token may have been revoked or the signing key rotated - get a fresh one and retry once
            tokenProvider.invalidate();
            response = send(uri, tokenProvider.getAccessToken());
        }

        if (response.statusCode() != 200) {
            throw new Exception("DataSteward DPA check for " + organisationId + " failed: HTTP "
                    + response.statusCode() + " from " + uri + " - " + abbreviate(response.body()));
        }

        String body = response.body() == null ? "" : response.body().trim();
        if (body.equalsIgnoreCase("true")) {
            return true;
        }
        if (body.equalsIgnoreCase("false")) {
            return false;
        }
        throw new Exception("Unexpected DataSteward DPA response for " + organisationId + ": " + abbreviate(body));
    }

    private HttpResponse<String> send(URI uri, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "text/plain")
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** Same normalisation DataSteward applies: strip braces, and use the parent of a parent_child code. */
    static String normalise(String organisationId) {
        String normalised = organisationId.trim().replaceAll("[{}]", "");
        int underscore = normalised.indexOf('_');
        if (underscore != -1) {
            normalised = normalised.substring(0, underscore);
        }
        return normalised;
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
