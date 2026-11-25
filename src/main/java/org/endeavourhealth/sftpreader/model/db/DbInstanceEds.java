package org.endeavourhealth.sftpreader.model.db;

import org.apache.commons.lang3.StringUtils;

/**
 * VHP port: these values used to be the single row of the {@code configuration_eds} table. They are now read
 * from the config database by {@link org.endeavourhealth.sftpreader.model.InstanceEdsConfig}. The
 * {@code use_keycloak} flag and the {@code keycloak_*} fields are gone: the reader always authenticates with
 * its Casdoor service token ({@link org.endeavourhealth.sftpreader.casdoor.ServiceAuthConfig}).
 *
 * <p>The instance-wide object ({@link DbInstance#getEdsConfiguration()}) holds the defaults from the
 * {@code storage} config row. Anything that works on one configuration's files must use
 * {@link DbInstance#getEdsConfiguration(DbConfiguration)} instead, which applies that configuration's own
 * {@code temp_directory} / {@code shared_storage_path} (columns of the SFTP Reader DB's {@code configuration}
 * table) where they are set.
 */
public class DbInstanceEds {
    private String edsUrl;
    private String tempDirectory;
    private String sharedStoragePath;
    //the instance-wide shared_storage_path (storage config row); the queue reader's shared_storage_path is
    //expected to match it, so files under it are notified with paths relative to it, as they always were
    private String defaultSharedStoragePath;

    /** The Messaging API address to post to ({@code api_url} in the {@code api} config row). */
    public String getEdsUrl() {
        return edsUrl;
    }

    public DbInstanceEds setEdsUrl(String edsUrl) {
        this.edsUrl = edsUrl;
        return this;
    }

    public String getTempDirectory() {
        return tempDirectory;
    }

    public DbInstanceEds setTempDirectory(String tempDirectory) {
        this.tempDirectory = tempDirectory;
        return this;
    }

    public String getSharedStoragePath() {
        return sharedStoragePath;
    }

    public DbInstanceEds setSharedStoragePath(String sharedStoragePath) {
        this.sharedStoragePath = sharedStoragePath;
        return this;
    }

    /** The instance-wide shared storage root (the {@code storage} config row), whatever this object's own root is. */
    public String getDefaultSharedStoragePath() {
        return defaultSharedStoragePath != null ? defaultSharedStoragePath : sharedStoragePath;
    }

    /**
     * True when this object's shared storage root is not the instance-wide one. Notifications for such a
     * configuration carry absolute file paths, because the queue reader can't know the root otherwise.
     */
    public boolean isSharedStoragePathOverridden() {
        return !samePath(sharedStoragePath, getDefaultSharedStoragePath());
    }

    /**
     * Returns a copy of these (instance-wide) settings with the configuration's own {@code temp_directory} and
     * {@code shared_storage_path} applied where they are set. This object is shared by every configuration's
     * task thread, so it is never changed.
     */
    public DbInstanceEds forConfiguration(DbConfiguration configuration) {
        DbInstanceEds ret = new DbInstanceEds()
                .setEdsUrl(edsUrl)
                .setTempDirectory(tempDirectory)
                .setSharedStoragePath(sharedStoragePath);
        ret.defaultSharedStoragePath = getDefaultSharedStoragePath();

        if (configuration != null) {
            String temp = StringUtils.trimToNull(configuration.getTempDirectory());
            if (temp != null) {
                ret.setTempDirectory(temp);
            }
            String shared = StringUtils.trimToNull(configuration.getSharedStoragePath());
            if (shared != null) {
                //FileHelper and the notification creator expect no trailing separator
                ret.setSharedStoragePath(StringUtils.stripEnd(shared, "/\\"));
            }
        }
        return ret;
    }

    private static boolean samePath(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        return StringUtils.stripEnd(a, "/\\").equals(StringUtils.stripEnd(b, "/\\"));
    }
}
