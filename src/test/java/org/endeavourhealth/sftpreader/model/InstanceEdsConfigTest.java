package org.endeavourhealth.sftpreader.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.endeavourhealth.sftpreader.model.db.DbInstanceEds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InstanceEdsConfigTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String API_ROW = "{\"api_url\":\"https://host:8443/vhp-api/api/PostMessageAsync\"}";
    private static final String STORAGE_ROW = "{\"temp_directory\":\"/tmp/sftp\",\"shared_storage_path\":\"s3://bucket/path\"}";

    private static JsonNode json(String s) throws Exception {
        return MAPPER.readTree(s);
    }

    @Test
    void readsTheMessagingApiAddressAndStoragePaths() throws Exception {
        DbInstanceEds eds = InstanceEdsConfig.fromJson(json(API_ROW), json(STORAGE_ROW), "sftpreader");

        assertEquals("https://host:8443/vhp-api/api/PostMessageAsync", eds.getEdsUrl());
        assertEquals("/tmp/sftp", eds.getTempDirectory());
        assertEquals("s3://bucket/path", eds.getSharedStoragePath());
    }

    @Test
    void missingApiRowSaysWhatToAdd() throws Exception {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> InstanceEdsConfig.fromJson(null, json(STORAGE_ROW), "sftpreader"));
        assertTrue(ex.getMessage().contains("'api'") && ex.getMessage().contains("api_url")
                && ex.getMessage().contains("sftpreader"), ex.getMessage());
    }

    @Test
    void missingStorageRowSaysWhatToAdd() throws Exception {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> InstanceEdsConfig.fromJson(json(API_ROW), null, "sftpreader"));
        assertTrue(ex.getMessage().contains("'storage'") && ex.getMessage().contains("shared_storage_path"),
                ex.getMessage());
    }

    @Test
    void blankValuesAreErrors() throws Exception {
        assertThrows(IllegalStateException.class,
                () -> InstanceEdsConfig.fromJson(json("{\"api_url\":\" \"}"), json(STORAGE_ROW), "sftpreader"));
        assertThrows(IllegalStateException.class,
                () -> InstanceEdsConfig.fromJson(json(API_ROW), json("{\"temp_directory\":\"/tmp\"}"), "sftpreader"));
    }
}
