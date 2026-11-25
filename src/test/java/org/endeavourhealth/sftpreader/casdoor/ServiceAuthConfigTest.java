package org.endeavourhealth.sftpreader.casdoor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServiceAuthConfigTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String s) throws Exception {
        return MAPPER.readTree(s);
    }

    private static final String AUTH_ROW = "{\"casdoor-endpoint\":\"https://auth.example\",\"org-name\":\"voror\","
            + "\"app-name\":\"vhp-sftpreader\",\"client-id\":\"cid\",\"client-secret\":\"csecret\"}";

    @Test
    void clientCredentialsComeFromAuthRowAndUserFromServiceRow() throws Exception {
        ServiceAuthConfig c = ServiceAuthConfig.fromJson(
                json("{\"username\":\"svc-sftpreader\",\"password\":\"pw\",\"timeout-seconds\":12}"), json(AUTH_ROW));

        assertEquals("https://auth.example", c.casdoorEndpoint());
        assertEquals("cid", c.clientId());
        assertEquals("csecret", c.clientSecret());
        assertEquals("svc-sftpreader", c.username());
        assertEquals("pw", c.password());
        assertEquals(12, c.timeout().toSeconds());
    }

    @Test
    void readsTheSameDatastewardRowAsTheMessagingApi() throws Exception {
        assertEquals("datasteward", ServiceAuthConfig.CONFIG_ID);

        //the row as deployed: DataSteward url and the service user together
        ServiceAuthConfig c = ServiceAuthConfig.fromJson(
                json("{\"url\":\"https://host/vhp-datasteward/machine-api\",\"username\":\"svc-sftpreader\",\"password\":\"pw\"}"),
                json(AUTH_ROW));

        assertEquals("svc-sftpreader", c.username());
        assertEquals("pw", c.password());
        assertEquals("cid", c.clientId());
        assertTrue(c.toString().contains("password as svc-sftpreader"));
    }

    @Test
    void serviceRowOverridesAuthRow() throws Exception {
        ServiceAuthConfig c = ServiceAuthConfig.fromJson(
                json("{\"casdoor-endpoint\":\"https://other\",\"client-id\":\"cid2\",\"client-secret\":\"s2\"}"), json(AUTH_ROW));

        assertEquals("https://other", c.casdoorEndpoint());
        assertEquals("cid2", c.clientId());
        assertEquals("s2", c.clientSecret());
        assertNull(c.username());
    }

    @Test
    void authRowAloneGivesClientCredentialsGrant() throws Exception {
        ServiceAuthConfig c = ServiceAuthConfig.fromJson(null, json(AUTH_ROW));

        assertNull(c.username());
        assertEquals(30, c.timeout().toSeconds());
        assertTrue(c.toString().contains("client_credentials"));
    }

    @Test
    void usernameIsNeverTakenFromTheSharedAuthRow() throws Exception {
        ServiceAuthConfig c = ServiceAuthConfig.fromJson(null,
                json("{\"casdoor-endpoint\":\"e\",\"client-id\":\"c\",\"client-secret\":\"s\",\"username\":\"u\",\"password\":\"p\"}"));
        assertNull(c.username());
        assertNull(c.password());
    }

    @Test
    void missingClientCredentialsIsAnError() throws Exception {
        assertThrows(IllegalStateException.class,
                () -> ServiceAuthConfig.fromJson(json("{\"username\":\"u\",\"password\":\"p\"}"), null));
    }

    @Test
    void usernameWithoutPasswordIsAnError() throws Exception {
        assertThrows(IllegalStateException.class,
                () -> ServiceAuthConfig.fromJson(json("{\"username\":\"u\"}"), json(AUTH_ROW)));
    }

    @Test
    void toStringNeverContainsSecrets() throws Exception {
        ServiceAuthConfig c = ServiceAuthConfig.fromJson(json("{\"username\":\"svc\",\"password\":\"pw-SECRET\"}"), json(AUTH_ROW));
        String s = c.toString();
        assertFalse(s.contains("pw-SECRET"), s);
        assertFalse(s.contains("csecret"), s);
        assertTrue(s.contains("password as svc"), s);
    }
}
