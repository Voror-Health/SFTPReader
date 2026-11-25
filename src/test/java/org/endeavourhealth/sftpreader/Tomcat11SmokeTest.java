package org.endeavourhealth.sftpreader;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.ApplicationParameter;
import org.endeavourhealth.sftpreader.web.SftpReaderRuntime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deploys the webapp (real web.xml and META-INF/context.xml) on an embedded Tomcat 11 and calls it over HTTP.
 *
 * <p>The Casdoor valve named in context.xml is replaced on the test classpath by a stand-in that takes the
 * user/roles from X-Test-* headers and applies web.xml's protected_paths / required_roles. The instance name
 * is set the way a real deployment would (a {@code <Parameter override="false">}). There is no config DB, so
 * the reader thread gets as far as ConfigManager and must then report FAILED without holding up Tomcat.</p>
 */
@Timeout(180)
class Tomcat11SmokeTest {

    private static Tomcat tomcat;
    private static String base;
    private static final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void start(@TempDir Path tempDir) throws Exception {
        tomcat = new Tomcat();
        tomcat.setBaseDir(tempDir.toString());
        tomcat.setPort(0);
        tomcat.getConnector();
        Context ctx = tomcat.addWebapp("/vhp-sftpreader", new File("src/main/webapp").getAbsolutePath());
        ctx.setParentClassLoader(Tomcat11SmokeTest.class.getClassLoader());

        ApplicationParameter instance = new ApplicationParameter();
        instance.setName("instance_name");
        instance.setValue("SMOKETEST01");
        instance.setOverride(false);
        ctx.addApplicationParameter(instance);

        ApplicationParameter wait = new ApplicationParameter();
        wait.setName("shutdown_wait_seconds");
        wait.setValue("5");
        wait.setOverride(false);
        ctx.addApplicationParameter(wait);

        long t0 = System.nanoTime();
        tomcat.start();
        long startMillis = (System.nanoTime() - t0) / 1_000_000;
        assertEquals("STARTED", ctx.getStateName(), "webapp failed to start - see log");
        assertTrue(startMillis < 60_000, "deployment should not wait for the reader thread (took " + startMillis + "ms)");

        base = "http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + "/vhp-sftpreader";
    }

    @AfterAll
    static void stop() throws Exception {
        if (tomcat != null) {
            tomcat.stop();
            tomcat.destroy();
        }
    }

    private static HttpResponse<String> get(String path, String user, String roles) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(60));
        if (user != null) {
            b.header("X-Test-User", user);
        }
        if (roles != null) {
            b.header("X-Test-Roles", roles);
        }
        return http.send(b.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void statusNeedsAToken() throws Exception {
        assertEquals(401, get("/api/status", null, null).statusCode());
    }

    @Test
    void statusNeedsARequiredRole() throws Exception {
        assertEquals(403, get("/api/status", "someone", "vhp_ui_user").statusCode());
    }

    @Test
    void statusForAdminAndService() throws Exception {
        HttpResponse<String> admin = get("/api/status", "alice", "vhp_ui_admin");
        assertEquals(200, admin.statusCode(), admin.body());
        assertTrue(admin.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        assertTrue(admin.body().contains("\"instanceName\":\"SMOKETEST01\""), admin.body());

        assertEquals(200, get("/api/status", "svc-monitor", "vhp_service").statusCode());
    }

    @Test
    void readerFailsCleanlyWithoutConfigDatabase() throws Exception {
        // the reader thread tries ConfigManager.initialize("sftpreader", "SMOKETEST01") against an unreachable DB
        long deadline = System.currentTimeMillis() + 120_000;
        while (SftpReaderRuntime.getState() == SftpReaderRuntime.State.STARTING && System.currentTimeMillis() < deadline) {
            Thread.sleep(250);
        }
        assertEquals(SftpReaderRuntime.State.FAILED, SftpReaderRuntime.getState());
        assertEquals("SMOKETEST01", SftpReaderRuntime.getInstanceName());

        HttpResponse<String> r = get("/api/status", "alice", "vhp_ui_admin");
        assertTrue(r.body().contains("\"state\":\"FAILED\""), r.body());
        assertTrue(r.body().contains("\"configurations\":[]"), r.body());
    }

    @Test
    void staticPageIsOpen() throws Exception {
        HttpResponse<String> r = get("/", null, null);
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("SFTP Reader Management Interface"));
        assertEquals(200, get("/index.js", null, null).statusCode());
        assertEquals(200, get("/js/jquery.min.js", null, null).statusCode());
    }

    @Test
    void unknownApiPathIs404ForAuthorisedUser() throws Exception {
        assertEquals(404, get("/api/nothing-here", "alice", "vhp_ui_admin").statusCode());
    }
}
