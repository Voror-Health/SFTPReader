package org.endeavourhealth.sftpreader.web;

import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import org.endeavourhealth.common.config.ConfigManager;
import org.endeavourhealth.common.utility.MetricsHelper;
import org.endeavourhealth.core.application.ApplicationHeartbeatHelper;
import org.endeavourhealth.core.database.dal.usermanager.caching.CacheManager;
import org.endeavourhealth.core.database.rdbms.ConnectionManager;
import org.endeavourhealth.sftpreader.Configuration;
import org.endeavourhealth.sftpreader.SftpReaderServices;
import org.endeavourhealth.sftpreader.SftpReaderTaskScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Provider;
import java.security.Security;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;

/**
 * VHP port: runs the SFTP Reader inside Tomcat. Does what {@code Main.main} does when started with just an
 * instance name (no utility command): initialise ConfigManager as {@code sftpreader/<instance>}, load the
 * instance's configurations, start the heartbeats and run {@link SftpReaderTaskScheduler}.
 *
 * <p>Everything runs on a background thread so a slow or unreachable database never holds up Tomcat. One
 * deployment runs one instance, as one JVM did before. The instance name comes from, in order:</p>
 * <ol>
 *     <li>the {@code instance_name} context-param (set per deployment with a {@code <Parameter>} in
 *     {@code conf/Catalina/<host>/<context>.xml}, or in web.xml)</li>
 *     <li>the {@code INSTANCE_NAME} system property (what the original jar also accepted)</li>
 *     <li>the {@code INSTANCE_NAME} environment variable</li>
 * </ol>
 * <p>If none is set the webapp still deploys (the status page reports NOT_CONFIGURED) but reads nothing.</p>
 */
public final class SftpReaderContextListener implements ServletContextListener {
    private static final Logger LOG = LoggerFactory.getLogger(SftpReaderContextListener.class);

    static final String APP_ID_PARAM = "app_id";
    static final String INSTANCE_NAME_PARAM = "instance_name";
    static final String INSTANCE_NAME_PROPERTY = "INSTANCE_NAME";
    /** how long undeploy waits for a running task to finish before interrupting it */
    static final String SHUTDOWN_WAIT_PARAM = "shutdown_wait_seconds";

    private Thread readerThread;
    private volatile SftpReaderTaskScheduler scheduler;
    private long shutdownWaitMillis = 60_000;
    private boolean configManagerStarted = false;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext ctx = event.getServletContext();
        SftpReaderRuntime.reset();

        String appId = firstNonBlank(ctx.getInitParameter(APP_ID_PARAM), "sftpreader");
        String instanceName = resolveInstanceName(ctx.getInitParameter(INSTANCE_NAME_PARAM));
        String wait = ctx.getInitParameter(SHUTDOWN_WAIT_PARAM);
        if (wait != null && !wait.isBlank()) {
            shutdownWaitMillis = Long.parseLong(wait.trim()) * 1000L;
        }

        if (instanceName == null) {
            String msg = "No SFTP Reader instance name: set the '" + INSTANCE_NAME_PARAM + "' context-param or "
                    + INSTANCE_NAME_PROPERTY + " - nothing will be read";
            LOG.error(msg);
            SftpReaderRuntime.setState(SftpReaderRuntime.State.NOT_CONFIGURED, msg);
            return;
        }

        SftpReaderRuntime.setInstanceName(instanceName);
        SftpReaderRuntime.setState(SftpReaderRuntime.State.STARTING, null);
        configManagerStarted = true;

        readerThread = new Thread(() -> run(appId, instanceName), "sftpreader-" + instanceName);
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void run(String appId, String instanceName) {
        try {
            ConfigManager.initialize(appId, instanceName);

            SftpReaderServices.initialise();

            Configuration configuration = Configuration.getInstance();

            LOG.info("--------------------------------------------------");
            LOG.info("SFTP Reader " + instanceName + " (Tomcat)");
            LOG.info("--------------------------------------------------");
            LOG.info("Instance " + configuration.getInstanceName() + " on host " + configuration.getMachineName());
            LOG.info("Processing configuration(s): " + configuration.getConfigurationIdsForDisplay());

            SftpReaderTaskScheduler taskScheduler = new SftpReaderTaskScheduler(configuration);
            this.scheduler = taskScheduler;
            SftpReaderRuntime.setRunning(configuration, taskScheduler);

            //start both these off, so we know this app is running
            MetricsHelper.startHeartbeat();
            ApplicationHeartbeatHelper.start(taskScheduler); //this cannot work if SFTP Reader cannot access core DB server

            SftpReaderRuntime.setState(SftpReaderRuntime.State.RUNNING, null);

            //and start working (returns when stop() is called)
            taskScheduler.start();

            SftpReaderRuntime.setState(SftpReaderRuntime.State.STOPPED, null);

        } catch (InterruptedException ie) {
            LOG.info("SFTP Reader thread interrupted");
            SftpReaderRuntime.setState(SftpReaderRuntime.State.STOPPED, null);
            Thread.currentThread().interrupt();

        } catch (Throwable t) {
            LOG.error("Fatal exception occurred - SFTP Reader " + instanceName + " is not running", t);
            SftpReaderRuntime.setState(SftpReaderRuntime.State.FAILED, t.getClass().getName() + ": " + t.getMessage());
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        LOG.info("Shutting down...");

        if (readerThread != null) {
            SftpReaderRuntime.setState(SftpReaderRuntime.State.STOPPING, null);
            SftpReaderTaskScheduler s = scheduler;
            if (s != null) {
                s.stop();
            }
            try {
                //let a running task finish (the scheduler checks the stop flag between tasks / every 5s)
                readerThread.join(shutdownWaitMillis);
                if (readerThread.isAlive()) {
                    LOG.warn("SFTP Reader still busy after {}s - interrupting it", shutdownWaitMillis / 1000);
                    readerThread.interrupt();
                    readerThread.join(10_000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (SftpReaderRuntime.getState() == SftpReaderRuntime.State.STOPPING) {
                SftpReaderRuntime.setState(SftpReaderRuntime.State.STOPPED, null);
            }
        }

        ApplicationHeartbeatHelper.stop();
        SftpReaderServices.shutdown();
        removeBouncyCastleProvider();

        if (configManagerStarted) {
            cleanupConnectionManager();
            cleanupCacheService();
        }
        cleanupJDBCDrivers();

        if (configManagerStarted) {
            // Cleanup logback last; only use console from this point
            System.out.println("Cleaning up ConfigManager/Logback...");
            ConfigManager.shutdownLogback();
        }
        System.out.println("...Shutdown done.");
    }

    static String resolveInstanceName(String contextParam) {
        return firstNonBlank(contextParam,
                firstNonBlank(System.getProperty(INSTANCE_NAME_PROPERTY), System.getenv(INSTANCE_NAME_PROPERTY)));
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        return b == null || b.isBlank() ? null : b.trim();
    }

    /**
     * PgpUtil registers BouncyCastle as a JCE provider. Under Tomcat that provider belongs to this webapp's
     * class loader, so remove it again on undeploy or it pins the old webapp in memory after a redeploy.
     */
    private static void removeBouncyCastleProvider() {
        Provider bc = Security.getProvider("BC");
        if (bc != null && bc.getClass().getClassLoader() == SftpReaderContextListener.class.getClassLoader()) {
            Security.removeProvider("BC");
            LOG.trace("Removed BouncyCastle security provider");
        }
    }

    private static void cleanupConnectionManager() {
        try {
            //the reader's own database pool ('database' config row)
            org.endeavourhealth.sftpreader.model.SftpReaderDb.shutdown();
        } catch (Throwable t) {
            LOG.error("Error shutting down the SFTP Reader database pool", t);
        }
        try {
            ConnectionManager.shutdown();
        } catch (Throwable t) {
            LOG.error("Error shutting down connection manager", t);
        }
    }

    private static void cleanupCacheService() {
        try {
            CacheManager.stopScheduler();
        } catch (Throwable t) {
            LOG.error("Error stopping cache service", t);
        }
    }

    private static void cleanupJDBCDrivers() {
        try {
            AbandonedConnectionCleanupThread.checkedShutdown();
        } catch (Throwable t) {
            LOG.trace("MySQL cleanup thread shutdown failed", t);
        }

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            if (driver.getClass().getClassLoader() == cl) {
                try {
                    LOG.info("Deregistering JDBC driver {}", driver);
                    DriverManager.deregisterDriver(driver);
                } catch (SQLException ex) {
                    LOG.error("Error deregistering JDBC driver {}", driver, ex);
                }
            }
        }
    }
}
