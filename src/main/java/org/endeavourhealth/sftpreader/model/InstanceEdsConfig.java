package org.endeavourhealth.sftpreader.model;

import com.fasterxml.jackson.databind.JsonNode;
import org.endeavourhealth.common.config.ConfigManager;
import org.endeavourhealth.sftpreader.model.db.DbInstanceEds;

/**
 * VHP port: the settings that used to be the single row of the SFTP Reader database's
 * {@code configuration_eds} table now come from the config database (app_id {@code sftpreader}, or
 * {@code global}), like the rest of the VHP apps' configuration.
 *
 * <table>
 *     <caption>Where each configuration_eds column went</caption>
 *     <tr><td>eds_url</td><td>config row {@code api}: {@code {"api_url": "https://<host>/vhp-api/api/PostMessageAsync"}}</td></tr>
 *     <tr><td>temp_directory, shared_storage_path</td><td>config row {@code storage}:
 *     {@code {"temp_directory": "...", "shared_storage_path": "..."}}</td></tr>
 *     <tr><td>use_keycloak</td><td>gone - the reader always sends its Casdoor service token</td></tr>
 *     <tr><td>keycloak_token_uri, keycloak_realm, keycloak_clientid</td><td>the standard {@code auth} row
 *     ({@code casdoor-endpoint}, {@code org-name}, {@code client-id}, {@code client-secret})</td></tr>
 *     <tr><td>keycloak_username, keycloak_password</td><td>the service user in the {@code datasteward} row
 *     (see {@link org.endeavourhealth.sftpreader.casdoor.ServiceAuthConfig})</td></tr>
 * </table>
 */
public final class InstanceEdsConfig {

    public static final String API_CONFIG_ID = "api";
    public static final String STORAGE_CONFIG_ID = "storage";

    private InstanceEdsConfig() {
    }

    public static DbInstanceEds fromConfigManager() throws Exception {
        JsonNode api = ConfigManager.getConfigurationAsJson(API_CONFIG_ID);
        JsonNode storage = ConfigManager.getConfigurationAsJson(STORAGE_CONFIG_ID);
        return fromJson(api, storage, ConfigManager.getAppId());
    }

    static DbInstanceEds fromJson(JsonNode api, JsonNode storage, String appId) {
        if (api == null) {
            throw new IllegalStateException("No '" + API_CONFIG_ID + "' config found for app_id " + appId
                    + " - it must give the Messaging API address as {\"api_url\": \"...\"}"
                    + " (this replaces configuration_eds.eds_url)");
        }
        if (storage == null) {
            throw new IllegalStateException("No '" + STORAGE_CONFIG_ID + "' config found for app_id " + appId
                    + " - it must give {\"temp_directory\": \"...\", \"shared_storage_path\": \"...\"}"
                    + " (this replaces those columns of configuration_eds)");
        }

        return new DbInstanceEds()
                .setEdsUrl(required(api, "api_url", API_CONFIG_ID))
                .setTempDirectory(required(storage, "temp_directory", STORAGE_CONFIG_ID))
                .setSharedStoragePath(required(storage, "shared_storage_path", STORAGE_CONFIG_ID));
    }

    private static String required(JsonNode node, String field, String configId) {
        String value = node.path(field).asText("").trim();
        if (value.isEmpty()) {
            throw new IllegalStateException("'" + field + "' must be set in the '" + configId + "' config");
        }
        return value;
    }
}
