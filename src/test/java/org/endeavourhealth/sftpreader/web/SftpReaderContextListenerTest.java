package org.endeavourhealth.sftpreader.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SftpReaderContextListenerTest {

    @AfterEach
    void clear() {
        System.clearProperty(SftpReaderContextListener.INSTANCE_NAME_PROPERTY);
    }

    @Test
    void contextParamWins() {
        System.setProperty(SftpReaderContextListener.INSTANCE_NAME_PROPERTY, "FROM_PROP");
        assertEquals("EMIS01", SftpReaderContextListener.resolveInstanceName(" EMIS01 "));
    }

    @Test
    void blankContextParamFallsBackToSystemProperty() {
        System.setProperty(SftpReaderContextListener.INSTANCE_NAME_PROPERTY, "FROM_PROP");
        assertEquals("FROM_PROP", SftpReaderContextListener.resolveInstanceName(""));
        assertEquals("FROM_PROP", SftpReaderContextListener.resolveInstanceName(null));
    }

    @Test
    void nothingSetGivesNull() {
        // INSTANCE_NAME env var is not set by the build
        if (System.getenv(SftpReaderContextListener.INSTANCE_NAME_PROPERTY) == null) {
            assertNull(SftpReaderContextListener.resolveInstanceName("  "));
        }
    }
}
