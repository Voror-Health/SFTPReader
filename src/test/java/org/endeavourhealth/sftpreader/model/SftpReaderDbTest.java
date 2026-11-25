package org.endeavourhealth.sftpreader.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SftpReaderDbTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String s) throws Exception {
        return MAPPER.readTree(s);
    }

    /** The row as the other VHP apps have it, with the nested pool block. */
    private static final String ROW = "{\n"
            + "  \"url\" : \"jdbc:mysql://localhost:3306/sftpreader?cachePrepStmts=true&prepStmtCacheSize=250\",\n"
            + "  \"username\" : \"sftpreader\",\n"
            + "  \"password\" : \"pw-SECRET\",\n"
            + "  \"pool\" : { \"min-idle\" : 2, \"max-pool-size\" : 10 }\n"
            + "}";

    @Test
    void configRowIsCalledDatabase() {
        assertEquals("database", SftpReaderDb.CONFIG_ID);
    }

    @Test
    void readsTheSharedVhpDatabaseRowFormat() throws Exception {
        SftpReaderDb.Settings s = SftpReaderDb.Settings.fromJson(json(ROW), "sftpreader");

        assertEquals("jdbc:mysql://localhost:3306/sftpreader?cachePrepStmts=true&prepStmtCacheSize=250", s.url());
        assertEquals("sftpreader", s.username());
        assertEquals("pw-SECRET", s.password());
        assertEquals(2, s.minIdle());
        assertEquals(10, s.maxPoolSize());
    }

    @Test
    void poolBlockIsOptional() throws Exception {
        SftpReaderDb.Settings s = SftpReaderDb.Settings.fromJson(
                json("{\"url\":\"jdbc:mysql://h/db\",\"username\":\"u\",\"password\":\"p\"}"), "sftpreader");

        assertEquals(1, s.minIdle());
        assertEquals(3, s.maxPoolSize());
    }

    @Test
    void missingRowSaysWhatToAdd() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> SftpReaderDb.Settings.fromJson(null, "sftpreader"));
        assertTrue(ex.getMessage().contains("'database'") && ex.getMessage().contains("sftpreader")
                && ex.getMessage().contains("db_sftp_reader"), ex.getMessage());
    }

    @Test
    void onlyMySqlIsAccepted() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> SftpReaderDb.Settings.fromJson(
                        json("{\"url\":\"jdbc:postgresql://h/db\",\"username\":\"u\",\"password\":\"p\"}"), "sftpreader"));
        assertTrue(ex.getMessage().contains("MySQL"), ex.getMessage());
    }

    @Test
    void missingFieldsAndBadPoolAreErrors() {
        assertThrows(IllegalStateException.class, () -> SftpReaderDb.Settings.fromJson(
                json("{\"url\":\"jdbc:mysql://h/db\",\"username\":\"u\"}"), "sftpreader"));
        assertThrows(IllegalStateException.class, () -> SftpReaderDb.Settings.fromJson(
                json("{\"username\":\"u\",\"password\":\"p\"}"), "sftpreader"));
        assertThrows(IllegalStateException.class, () -> SftpReaderDb.Settings.fromJson(
                json("{\"url\":\"jdbc:mysql://h/db\",\"username\":\"u\",\"password\":\"p\",\"pool\":{\"min-idle\":5,\"max-pool-size\":2}}"),
                "sftpreader"));
    }

    @Test
    void toStringNeverContainsThePassword() throws Exception {
        String s = SftpReaderDb.Settings.fromJson(json(ROW), "sftpreader").toString();
        assertFalse(s.contains("pw-SECRET"), s);
        assertFalse(s.contains("cachePrepStmts"), s);
        assertTrue(s.contains("jdbc:mysql://localhost:3306/sftpreader"), s);
    }

    @Test
    void poolIsConfiguredLikeTheLibraryPoolWas() throws Exception {
        // HikariDataSource connects lazily, so no database is needed to check its settings
        try (HikariDataSource ds = SftpReaderDb.createPool(SftpReaderDb.Settings.fromJson(json(ROW), "sftpreader"))) {
            assertFalse(ds.isAutoCommit());
            assertEquals(2, ds.getMinimumIdle());
            assertEquals(10, ds.getMaximumPoolSize());
            assertEquals("sftpreader", ds.getUsername());
            assertTrue(ds.getJdbcUrl().startsWith("jdbc:mysql://localhost:3306/sftpreader"));
        }
    }
}
