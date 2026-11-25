package org.endeavourhealth.sftpreader.casdoor;

/**
 * Supplies the bearer token this service presents when it calls other VHP services (machine-to-machine).
 */
public interface ServiceTokenProvider {

    String getAccessToken() throws Exception;

    /** Drop any cached token, e.g. after the remote service rejected it with a 401. */
    void invalidate();
}
