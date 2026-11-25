package org.endeavourhealth.sftpreader.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.zaxxer.hikari.HikariDataSource;
import org.endeavourhealth.common.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * VHP port: connections to the SFTP Reader's own database.
 *
 * <p>They used to come from the Endeavour {@code ConnectionManager} ({@code getSftpReaderConnection()}), which
 * reads a config row called {@code db_sftp_reader}. The reader now reads the {@code database} row, in the same
 * format as the other VHP apps (app_id {@code sftpreader}, or {@code global}):</p>
 * <pre>
 * {
 *   "url": "jdbc:mysql://&lt;host&gt;:3306/sftpreader",            (required, MySQL only)
 *   "username": "...", "password": "...",                     (required)
 *   "pool": { "min-idle": 1, "max-pool-size": 3 }             (optional)
 * }
 * </pre>
 *
 * <p>Connections behave as the library's did: pooled ones come from a HikariCP pool with the same defaults,
 * and both kinds are handed out with auto-commit off. The other Endeavour databases (and the TPP hashes
 * database, {@code db_sftp_reader_hashes}) still go through the library's ConnectionManager.</p>
 */
public final class SftpReaderDb {
    private static final Logger LOG = LoggerFactory.getLogger(SftpReaderDb.class);

    public static final String CONFIG_ID = "database";

    /** What the {@code database} config row says. */
    public record Settings(String url, String username, String password, int minIdle, int maxPoolSize) {

        static final int DEFAULT_MIN_IDLE = 1;
        static final int DEFAULT_MAX_POOL_SIZE = 3;

        static Settings fromJson(JsonNode node, String appId) {
            if (node == null) {
                throw new IllegalStateException("No '" + CONFIG_ID + "' config found for app_id " + appId
                        + " - it must give the SFTP Reader database as {\"url\": \"jdbc:mysql://...\", \"username\": \"...\","
                        + " \"password\": \"...\"} (this replaces the 'db_sftp_reader' row)");
            }
            String url = required(node, "url");
            if (!url.startsWith("jdbc:mysql:")) {
                //VHP: the PostgreSQL data layer was removed; the SFTP Reader database is MySQL
                throw new IllegalStateException("The SFTP Reader database in the '" + CONFIG_ID
                        + "' config must be MySQL (a jdbc:mysql: url) - other databases are no longer supported");
            }
            String username = required(node, "username");
            String password = required(node, "password");

            JsonNode pool = node.path("pool");
            int minIdle = pool.path("min-idle").asInt(DEFAULT_MIN_IDLE);
            int maxPoolSize = pool.path("max-pool-size").asInt(DEFAULT_MAX_POOL_SIZE);
            if (maxPoolSize < 1 || minIdle < 0 || minIdle > maxPoolSize) {
                throw new IllegalStateException("'pool' in the '" + CONFIG_ID + "' config is not valid: min-idle="
                        + minIdle + ", max-pool-size=" + maxPoolSize);
            }
            return new Settings(url, username, password, minIdle, maxPoolSize);
        }

        private static String required(JsonNode node, String field) {
            String value = node.path(field).asText("").trim();
            if (value.isEmpty()) {
                throw new IllegalStateException("'" + field + "' must be set in the '" + CONFIG_ID + "' config");
            }
            return value;
        }

        @Override
        public String toString() {
            //never log the password; the url can carry one too, so drop its query string
            int q = url.indexOf('?');
            return "Settings[url=" + (q == -1 ? url : url.substring(0, q)) + ", username=" + username
                    + ", minIdle=" + minIdle + ", maxPoolSize=" + maxPoolSize + "]";
        }
    }

    private static volatile HikariDataSource pool;
    private static volatile Settings settings;

    private SftpReaderDb() {
    }

    private static Settings settings() throws Exception {
        Settings s = settings;
        if (s == null) {
            synchronized (SftpReaderDb.class) {
                s = settings;
                if (s == null) {
                    s = Settings.fromJson(ConfigManager.getConfigurationAsJson(CONFIG_ID), ConfigManager.getAppId());
                    settings = s;
                }
            }
        }
        return s;
    }

    /** A pooled connection, auto-commit off (as {@code ConnectionManager.getSftpReaderConnection()} gave). */
    public static Connection getConnection() throws Exception {
        HikariDataSource p = pool;
        if (p == null || p.isClosed()) {
            synchronized (SftpReaderDb.class) {
                p = pool;
                if (p == null || p.isClosed()) {
                    Settings s = settings();
                    p = createPool(s);
                    pool = p;
                    LOG.info("SFTP Reader database pool created: {}", s);
                }
            }
        }
        return p.getConnection();
    }

    /**
     * A connection that isn't from the pool, auto-commit off. For connections kept open a long time, e.g. the
     * configuration lock (as {@code ConnectionManager.getSftpReaderNonPooledConnection()} gave).
     */
    public static Connection getNonPooledConnection() throws Exception {
        Settings s = settings();
        Connection connection = DriverManager.getConnection(s.url(), s.username(), s.password());
        connection.setAutoCommit(false); //so this matches the pooled connections
        return connection;
    }

    /** Same pool defaults as the Endeavour ConnectionManager used for this database. */
    static HikariDataSource createPool(Settings s) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("sftpreader-" + CONFIG_ID);
        ds.setJdbcUrl(s.url());
        ds.setUsername(s.username());
        ds.setPassword(s.password());
        ds.setMinimumIdle(s.minIdle());
        ds.setMaximumPoolSize(s.maxPoolSize());
        ds.setIdleTimeout(60000);
        ds.setAutoCommit(false);
        ds.setConnectionTimeout(300000L);
        ds.setLeakDetectionThreshold(5000L);
        return ds;
    }

    public static synchronized void shutdown() {
        HikariDataSource p = pool;
        pool = null;
        settings = null;
        if (p != null) {
            p.close();
        }
    }
}
