package org.endeavourhealth.sftpreader.sender;

import org.endeavourhealth.sftpreader.datasteward.DataStewardClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DpaCheck {
    private static final Logger LOG = LoggerFactory.getLogger(DpaCheck.class);

    /**
     * Checks whether a DPA exists for the organisation.
     *
     * <p>VHP port: this used to call the Messaging API's {@code <messaging api>/dsm/hasDPA/<ods>} endpoint
     * (derived from the instance's eds_url) with a Keycloak token. DPA checks now go to the vhp-datasteward API
     * ({@code GET <datasteward>/organisation/hasDPA}) with the SFTP Reader's Casdoor token. As before, a failed
     * check throws rather than answering "no DPA".</p>
     */
    public static boolean checkForDpa(String organisationId) throws Exception {
        boolean hasDpa = DataStewardClient.getInstance().hasDpa(organisationId);
        LOG.trace("DPA check for " + organisationId + " -> " + hasDpa);
        return hasDpa;
    }
}
