package org.endeavourhealth.sftpreader.implementations;

import org.endeavourhealth.common.utility.JsonSerializer;
import org.endeavourhealth.sftpreader.implementations.homerton.HomertonNotificationCreator;
import org.endeavourhealth.sftpreader.model.DataLayerI;
import org.endeavourhealth.sftpreader.model.db.BatchSplit;
import org.endeavourhealth.sftpreader.model.db.DbConfiguration;
import org.endeavourhealth.sftpreader.model.db.DbInstance;
import org.endeavourhealth.sftpreader.model.db.DbInstanceEds;
import org.endeavourhealth.sftpreader.utilities.ExchangePayloadFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The notification sent to the Messaging API: paths relative to the shared root for configurations that use the
 * instance-wide root (unchanged), absolute paths for a configuration with its own shared_storage_path.
 * Runs the real notification creator over real files (file-system storage, Homerton file names).
 */
class NotificationPayloadPathTest {

    private static final String BATCH = "2026-11-25T08.30.00";

    private static DataLayerI noPreviousNotifications() {
        return (DataLayerI) Proxy.newProxyInstance(DataLayerI.class.getClassLoader(), new Class<?>[]{DataLayerI.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getNotifiedMessages")) {
                        return Collections.emptyList();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Path writeBatch(Path root) throws Exception {
        Path dir = Files.createDirectories(root.resolve("HOMERTON").resolve(BATCH));
        Files.writeString(dir.resolve("ph_d_person_demographics.csv"), "a,b\n1,2\n");
        return dir;
    }

    private static DbConfiguration homerton() {
        return new DbConfiguration()
                .setConfigurationId("HOMERTON")
                .setSoftwareContentType("HOMERTONCSV")
                .setSoftwareVersion("1")
                .setLocalRootPath("HOMERTON");
    }

    private static ExchangePayloadFile[] notify(DbInstanceEds eds, DbConfiguration c) throws Exception {
        BatchSplit split = new BatchSplit().setLocalRelativePath(BATCH);
        SftpNotificationCreator.PayloadWrapper payload = new HomertonNotificationCreator()
                .createNotificationMessage("ORG", noPreviousNotifications(), eds, c, split);
        return JsonSerializer.deserialize(payload.getPayload(), ExchangePayloadFile[].class);
    }

    @Test
    void sharedRootGivesRelativePaths(@TempDir Path tmp) throws Exception {
        Path shared = Files.createDirectories(tmp.resolve("shared"));
        writeBatch(shared);
        DbInstance instance = new DbInstance().setEdsConfiguration(new DbInstanceEds()
                .setTempDirectory(tmp.resolve("tmp").toString()).setSharedStoragePath(shared.toString()));

        DbConfiguration c = homerton();
        ExchangePayloadFile[] files = notify(instance.getEdsConfiguration(c), c);

        assertEquals(1, files.length);
        String path = files[0].getPath().replace('\\', '/');
        assertEquals("HOMERTON/" + BATCH + "/ph_d_person_demographics.csv", path);
    }

    @Test
    void ownRootGivesAbsolutePaths(@TempDir Path tmp) throws Exception {
        Path shared = Files.createDirectories(tmp.resolve("shared"));
        Path own = Files.createDirectories(tmp.resolve("own-root"));
        Path dir = writeBatch(own);
        DbInstance instance = new DbInstance().setEdsConfiguration(new DbInstanceEds()
                .setTempDirectory(tmp.resolve("tmp").toString()).setSharedStoragePath(shared.toString()));

        DbConfiguration c = homerton();
        c.setSharedStoragePath(own.toString());
        ExchangePayloadFile[] files = notify(instance.getEdsConfiguration(c), c);

        assertEquals(1, files.length);
        assertEquals(dir.resolve("ph_d_person_demographics.csv").toString(), files[0].getPath());
        assertEquals("person_demographics", files[0].getType());
    }
}
