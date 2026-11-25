package org.endeavourhealth.sftpreader.model.db;

import java.util.List;

public class DbInstance {
    private String instanceName;
    private Integer httpManagementPort;
    private List<String> configurationIds;
    //private DbInstanceSlack slackConfiguration;
    private DbInstanceEds edsConfiguration;

    public String getInstanceName() {
        return instanceName;
    }

    public DbInstance setInstanceName(String instanceName) {
        this.instanceName = instanceName;
        return this;
    }

    public Integer getHttpManagementPort() {
        return httpManagementPort;
    }

    public DbInstance setHttpManagementPort(Integer httpManagementPort) {
        this.httpManagementPort = httpManagementPort;
        return this;
    }

    public List<String> getConfigurationIds() {
        return configurationIds;
    }

    public DbInstance setConfigurationIds(List<String> configurationIds) {
        this.configurationIds = configurationIds;
        return this;
    }

    /*public DbInstanceSlack getSlackConfiguration() {
        return slackConfiguration;
    }

    public DbInstance setSlackConfiguration(DbInstanceSlack slackConfiguration) {
        this.slackConfiguration = slackConfiguration;
        return this;
    }*/

    /**
     * The instance-wide settings (the {@code api} and {@code storage} config rows). For anything that reads or
     * writes one configuration's files use {@link #getEdsConfiguration(DbConfiguration)}.
     */
    public DbInstanceEds getEdsConfiguration() {
        return this.edsConfiguration;
    }

    /**
     * VHP: the settings for one configuration: the instance-wide ones with that configuration's own
     * {@code temp_directory} / {@code shared_storage_path} applied where set. Returns a new object each time.
     */
    public DbInstanceEds getEdsConfiguration(DbConfiguration configuration) {
        if (this.edsConfiguration == null) {
            return null;
        }
        return this.edsConfiguration.forConfiguration(configuration);
    }

    public DbInstance setEdsConfiguration(DbInstanceEds edsConfiguration) {
        this.edsConfiguration = edsConfiguration;
        return this;
    }
}
