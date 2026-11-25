package org.endeavourhealth.sftpreader.casdoor;

import com.fasterxml.jackson.databind.JsonNode;
import org.endeavourhealth.common.config.ConfigManager;

import java.time.Duration;

/**
 * The Casdoor identity the SFTP Reader uses when it calls other VHP services (Messaging API, DataSteward).
 * It replaces the Keycloak settings in the SFTP Reader's {@code configuration_eds} table
 * ({@code keycloak_token_uri}, {@code keycloak_realm}, {@code keycloak_username}, {@code keycloak_password},
 * {@code keycloak_clientid}), which are no longer read for authentication.
 *
 * <p>It is read from the {@code datasteward} config row (app_id {@code sftpreader}, or {@code global}), the
 * same row and the same fields as vhp-messaging-api uses, so both apps are configured alike:</p>
 * <pre>
 * {
 *   "url": "https://&lt;host&gt;/vhp-datasteward/machine-api",   (read by DataStewardConfig)
 *   "username": "svc-sftpreader", "password": "...",          (optional - Casdoor service user, password grant)
 *   "casdoor-endpoint": "...", "client-id": "...", "client-secret": "...",   (optional, default: the auth row)
 *   "timeout-seconds": 30                                       (optional)
 * }
 * </pre>
 *
 * <p>The one identity is used for both DataSteward and the Messaging API. Casdoor endpoint and client credentials not given here are taken from the {@code auth} row, the row the
 * CasdoorAuthenticatorValve reads for this webapp. With a {@code username} the token is obtained with the
 * password grant as that service user, who needs the roles the called services require (Messaging API:
 * {@code vhp_service} + {@code vhp_messaging_post}; DataSteward: {@code vhp_service}). Without one, the
 * client_credentials grant is used, but application tokens carry no roles, so the valves on those services
 * currently refuse them (see docs/casdoor-app-tokens-and-roles.md).</p>
 */
public record ServiceAuthConfig(String casdoorEndpoint,
                                String clientId,
                                String clientSecret,
                                String username,
                                String password,
                                Duration timeout) {

    /** The same row as {@code DataStewardConfig.CONFIG_ID}; there is no separate service-identity row. */
    public static final String CONFIG_ID = "datasteward";
    public static final String AUTH_CONFIG_ID = "auth";

    public static ServiceAuthConfig fromConfigManager() throws Exception {
        JsonNode service = ConfigManager.getConfigurationAsJson(CONFIG_ID);
        JsonNode auth = ConfigManager.getConfigurationAsJson(AUTH_CONFIG_ID);
        if (service == null && auth == null) {
            throw new IllegalStateException("Neither a '" + CONFIG_ID + "' nor an '" + AUTH_CONFIG_ID + "' config row found for app_id "
                    + ConfigManager.getAppId() + " - calls to the Messaging API and DataSteward cannot be authenticated");
        }
        return fromJson(service, auth);
    }

    static ServiceAuthConfig fromJson(JsonNode service, JsonNode auth) {
        String endpoint = firstNonBlank(service, auth, "casdoor-endpoint");
        String clientId = firstNonBlank(service, auth, "client-id");
        String clientSecret = firstNonBlank(service, auth, "client-secret");
        if (endpoint == null || clientId == null || clientSecret == null) {
            throw new IllegalStateException("Casdoor client credentials (casdoor-endpoint, client-id, client-secret) must be set in the '"
                    + CONFIG_ID + "' or '" + AUTH_CONFIG_ID + "' config");
        }
        //service user credentials are only taken from the datasteward row, never the shared auth row
        String username = service == null ? null : blankToNull(service.path("username").asText(""));
        String password = service == null ? null : blankToNull(service.path("password").asText(""));
        if (username != null && password == null) {
            throw new IllegalStateException("'password' must be set in the '" + CONFIG_ID + "' config when 'username' is");
        }
        long timeoutSeconds = service == null ? 30 : service.path("timeout-seconds").asLong(30);
        return new ServiceAuthConfig(endpoint, clientId, clientSecret, username, password, Duration.ofSeconds(timeoutSeconds));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String firstNonBlank(JsonNode primary, JsonNode fallback, String field) {
        String value = primary == null ? "" : primary.path(field).asText("");
        if (value.isBlank() && fallback != null) {
            value = fallback.path(field).asText("");
        }
        return value.isBlank() ? null : value;
    }

    @Override
    public String toString() {
        //never log the client secret or password
        return "ServiceAuthConfig[casdoorEndpoint=" + casdoorEndpoint + ", clientId=" + clientId
                + ", grant=" + (username == null ? "client_credentials" : "password as " + username)
                + ", timeout=" + timeout + "]";
    }
}
