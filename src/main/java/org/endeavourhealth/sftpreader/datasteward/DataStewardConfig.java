package org.endeavourhealth.sftpreader.datasteward;

import com.fasterxml.jackson.databind.JsonNode;
import org.endeavourhealth.common.config.ConfigManager;

import java.time.Duration;

/**
 * Where the vhp-datasteward API is, read from the config database.
 *
 * <p>Config row {@code datasteward} (app_id {@code sftpreader}, or {@code global}):</p>
 * <pre>
 * {
 *   "url": "https://&lt;host&gt;/vhp-datasteward/machine-api",   (required)
 *   "cache-seconds": 300,                                     (optional, default 300)
 *   "timeout-seconds": 30,                                    (optional, default 30)
 *   "username": "...", "password": "...",                     (optional - Casdoor service user)
 *   "casdoor-endpoint": "...", "client-id": "...", "client-secret": "..."   (optional)
 * }
 * </pre>
 *
 * <p>This record holds the DataSteward address. The identity fields of the same row are read by
 * {@link org.endeavourhealth.sftpreader.casdoor.ServiceAuthConfig}: the bearer token is the SFTP Reader's
 * Casdoor service token, the same one sent to the Messaging API.</p>
 */
public record DataStewardConfig(String url, Duration cacheTtl, Duration timeout) {

    public static final String CONFIG_ID = "datasteward";

    public static DataStewardConfig fromConfigManager() throws Exception {
        JsonNode ds = ConfigManager.getConfigurationAsJson(CONFIG_ID);
        if (ds == null) {
            throw new IllegalStateException("No '" + CONFIG_ID + "' config found for app_id " + ConfigManager.getAppId()
                    + " - DPA checks cannot be made against the DataSteward API");
        }
        return fromJson(ds);
    }

    static DataStewardConfig fromJson(JsonNode ds) {
        String url = ds.path("url").asText("");
        if (url.isBlank()) {
            throw new IllegalStateException("'url' must be set in the '" + CONFIG_ID + "' config");
        }
        Duration cacheTtl = Duration.ofSeconds(ds.path("cache-seconds").asLong(300));
        Duration timeout = Duration.ofSeconds(ds.path("timeout-seconds").asLong(30));
        return new DataStewardConfig(url, cacheTtl, timeout);
    }
}
