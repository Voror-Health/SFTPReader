package org.endeavourhealth.sftpreader;

import org.endeavourhealth.sftpreader.casdoor.CasdoorServiceAuth;
import org.endeavourhealth.sftpreader.casdoor.ServiceAuthConfig;
import org.endeavourhealth.sftpreader.casdoor.ServiceTokenProvider;
import org.endeavourhealth.sftpreader.datasteward.DataStewardClient;
import org.endeavourhealth.sftpreader.datasteward.DataStewardConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VHP port: sets up the clients for the services the SFTP Reader calls, once ConfigManager is initialised.
 * Used by both {@link Main} (command line) and the Tomcat listener.
 *
 * <ul>
 *     <li>Casdoor service token, sent to the Messaging API (replaces {@code KeycloakClient}, which was
 *     initialised from the keycloak_* columns of the instance table)</li>
 *     <li>DataSteward client for DPA checks (replaces the Messaging API's {@code /dsm/hasDPA} and the DSM
 *     database lookup)</li>
 * </ul>
 *
 * <p>A missing or bad config does not stop the reader from starting (downloading, splitting and so on still
 * work), but notifying the Messaging API fails, with a clear error, until it is fixed. That is the same
 * place the old Keycloak/DPA failures showed up.</p>
 */
public final class SftpReaderServices {
    private static final Logger LOG = LoggerFactory.getLogger(SftpReaderServices.class);

    private SftpReaderServices() {
    }

    public static void initialise() {
        ServiceTokenProvider tokens = null;
        try {
            ServiceAuthConfig config = ServiceAuthConfig.fromConfigManager();
            tokens = CasdoorServiceAuth.create(config);
            CasdoorServiceAuth.initialise(tokens);
            LOG.info("Casdoor service authentication enabled: {}", config);
        } catch (Exception ex) {
            LOG.error("Failed to initialise Casdoor service authentication - calls to the Messaging API and DataSteward"
                    + " will fail until the 'datasteward'/'auth' config is fixed", ex);
        }

        if (tokens == null) {
            LOG.error("DataSteward client not initialised (no Casdoor service token) - DPA checks will fail");
            return;
        }

        try {
            DataStewardConfig config = DataStewardConfig.fromConfigManager();
            DataStewardClient.initialise(DataStewardClient.create(config, tokens));
            LOG.info("DataSteward DPA checks enabled: url={}, cacheTtl={}, timeout={}", config.url(), config.cacheTtl(), config.timeout());
        } catch (Exception ex) {
            LOG.error("Failed to initialise DataSteward client - DPA checks will fail until the 'datasteward' config is fixed", ex);
        }
    }

    public static void shutdown() {
        DataStewardClient.shutdown();
        CasdoorServiceAuth.shutdown();
    }
}
