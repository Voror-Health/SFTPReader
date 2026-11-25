package org.endeavourhealth.sftpreader.model.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Each configuration's temp_directory / shared_storage_path (SFTP Reader DB configuration table) override the
 * instance-wide storage config row for that configuration only.
 */
class DbInstanceEdsPerConfigurationTest {

    private static DbInstance instance() {
        return new DbInstance().setEdsConfiguration(new DbInstanceEds()
                .setEdsUrl("https://api/PostMessageAsync")
                .setTempDirectory("/sftp/tmp")
                .setSharedStoragePath("s3://main-bucket/sftpreader"));
    }

    @Test
    void noOverridesGiveTheStorageRow() {
        DbInstanceEds eds = instance().getEdsConfiguration(new DbConfiguration().setConfigurationId("C1"));
        assertEquals("/sftp/tmp", eds.getTempDirectory());
        assertEquals("s3://main-bucket/sftpreader", eds.getSharedStoragePath());
        assertEquals("https://api/PostMessageAsync", eds.getEdsUrl());
        assertFalse(eds.isSharedStoragePathOverridden());
    }

    @Test
    void blankColumnsCountAsUnset() {
        DbConfiguration c = new DbConfiguration().setConfigurationId("C1");
        c.setTempDirectory("  ");
        c.setSharedStoragePath("");
        DbInstanceEds eds = instance().getEdsConfiguration(c);
        assertEquals("/sftp/tmp", eds.getTempDirectory());
        assertEquals("s3://main-bucket/sftpreader", eds.getSharedStoragePath());
        assertFalse(eds.isSharedStoragePathOverridden());
    }

    @Test
    void tempDirectoryOnlyKeepsSharedRootAndRelativeNotifications() {
        DbConfiguration c = new DbConfiguration().setConfigurationId("C1");
        c.setTempDirectory("/fast-disk/tmp");
        DbInstanceEds eds = instance().getEdsConfiguration(c);
        assertEquals("/fast-disk/tmp", eds.getTempDirectory());
        assertEquals("s3://main-bucket/sftpreader", eds.getSharedStoragePath());
        assertFalse(eds.isSharedStoragePathOverridden());
    }

    @Test
    void ownSharedRootIsUsedAndFlagged() {
        DbConfiguration c = new DbConfiguration().setConfigurationId("C1");
        c.setSharedStoragePath("s3://other-bucket/feeds/");
        DbInstanceEds eds = instance().getEdsConfiguration(c);
        assertEquals("s3://other-bucket/feeds", eds.getSharedStoragePath(), "trailing separator removed");
        assertEquals("s3://main-bucket/sftpreader", eds.getDefaultSharedStoragePath());
        assertTrue(eds.isSharedStoragePathOverridden());
    }

    @Test
    void sharedRootEqualToTheDefaultIsNotAnOverride() {
        DbConfiguration c = new DbConfiguration().setConfigurationId("C1");
        c.setSharedStoragePath("s3://main-bucket/sftpreader/");
        assertFalse(instance().getEdsConfiguration(c).isSharedStoragePathOverridden());
    }

    @Test
    void configurationsDoNotLeakIntoEachOtherOrTheInstance() {
        DbInstance instance = instance();
        DbConfiguration a = new DbConfiguration().setConfigurationId("A");
        a.setTempDirectory("/a/tmp");
        a.setSharedStoragePath("/mnt/a");
        DbConfiguration b = new DbConfiguration().setConfigurationId("B");

        DbInstanceEds edsA = instance.getEdsConfiguration(a);
        DbInstanceEds edsB = instance.getEdsConfiguration(b);

        assertEquals("/mnt/a", edsA.getSharedStoragePath());
        assertEquals("s3://main-bucket/sftpreader", edsB.getSharedStoragePath());
        assertEquals("/sftp/tmp", edsB.getTempDirectory());
        assertEquals("s3://main-bucket/sftpreader", instance.getEdsConfiguration().getSharedStoragePath());
        assertEquals("/sftp/tmp", instance.getEdsConfiguration().getTempDirectory());
        assertNotSame(instance.getEdsConfiguration(), edsB);
    }
}
