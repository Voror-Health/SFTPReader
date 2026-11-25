package org.endeavourhealth.sftpreader.casdoor;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

/**
 * Replaces {@code org.endeavourhealth.common.security.keycloak.client.KeycloakClient} for the SFTP Reader's
 * outbound calls. Same shape as before: set up once, then {@code instance().getAuthorizationHeader()} gives the
 * header to add to an Apache HttpClient request.
 */
public final class CasdoorServiceAuth {

    private static volatile ServiceTokenProvider instance;

    private CasdoorServiceAuth() {
    }

    public static ServiceTokenProvider create(ServiceAuthConfig config) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return new CasdoorServiceTokenProvider(http, config.casdoorEndpoint(), config.clientId(), config.clientSecret(),
                config.username(), config.password(), config.timeout(), Clock.systemUTC());
    }

    public static void initialise(ServiceTokenProvider tokenProvider) {
        instance = tokenProvider;
    }

    public static void shutdown() {
        instance = null;
    }

    public static boolean isInitialised() {
        return instance != null;
    }

    public static ServiceTokenProvider getInstance() {
        ServiceTokenProvider provider = instance;
        if (provider == null) {
            throw new IllegalStateException("Casdoor service authentication is not initialised - check the '"
                    + ServiceAuthConfig.CONFIG_ID + "' / '" + ServiceAuthConfig.AUTH_CONFIG_ID
                    + "' config rows for app_id sftpreader (see the startup log)");
        }
        return provider;
    }

    /** {@code Authorization: Bearer <Casdoor access token>} */
    public static Header getAuthorizationHeader() throws Exception {
        return new BasicHeader("Authorization", "Bearer " + getInstance().getAccessToken());
    }
}
