package org.endeavourhealth.sftpreader.datasteward;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DataStewardConfigTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void readsUrlAndDefaults() throws Exception {
        DataStewardConfig c = DataStewardConfig.fromJson(MAPPER.readTree("{\"url\":\"https://h/vhp-datasteward/machine-api\"}"));
        assertEquals("https://h/vhp-datasteward/machine-api", c.url());
        assertEquals(300, c.cacheTtl().toSeconds());
        assertEquals(30, c.timeout().toSeconds());
    }

    @Test
    void readsOverrides() throws Exception {
        DataStewardConfig c = DataStewardConfig.fromJson(MAPPER.readTree("{\"url\":\"u\",\"cache-seconds\":5,\"timeout-seconds\":7}"));
        assertEquals(5, c.cacheTtl().toSeconds());
        assertEquals(7, c.timeout().toSeconds());
    }

    @Test
    void urlIsRequired() {
        assertThrows(IllegalStateException.class, () -> DataStewardConfig.fromJson(MAPPER.readTree("{}")));
    }
}
