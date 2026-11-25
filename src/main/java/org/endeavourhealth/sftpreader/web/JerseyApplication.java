package org.endeavourhealth.sftpreader.web;

import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;

/**
 * Jersey 3 application for the management API (/api/*). The original web.xml pointed Jersey 2 at
 * {@code org.endeavourhealth.sftpreader.management}, a package that no longer existed, and the embedded Jetty
 * that would have served it was never started. Authentication and role checks are done by the
 * CasdoorAuthenticatorValve before a request reaches Jersey.
 */
public class JerseyApplication extends ResourceConfig {

    public JerseyApplication() {
        register(StatusEndpoint.class);
        register(JacksonFeature.class);
    }
}
