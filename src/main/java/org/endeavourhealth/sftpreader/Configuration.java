package org.endeavourhealth.sftpreader;

import com.fasterxml.jackson.databind.JsonNode;
import com.kstruct.gethostname4j.Hostname;
import org.apache.commons.lang3.StringUtils;
import org.endeavourhealth.common.config.ConfigManager;
import org.endeavourhealth.sftpreader.model.SftpReaderDb;
import org.endeavourhealth.sftpreader.model.DataLayerI;

import org.endeavourhealth.sftpreader.model.MySqlDataLayer;
import org.endeavourhealth.sftpreader.model.db.DbConfiguration;
import org.endeavourhealth.sftpreader.model.db.DbInstance;
import org.endeavourhealth.sftpreader.model.exceptions.SftpReaderException;
import org.endeavourhealth.sftpreader.utilities.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class Configuration {
    private static final Logger LOG = LoggerFactory.getLogger(Configuration.class);

    private static Configuration instance = null;

    public static Configuration getInstance() throws Exception {
        if (instance == null) {
            synchronized (Configuration.class) {
                if (instance == null) {
                    instance = new Configuration();
                }
            }
        }

        return instance;
    }

    private DataLayerI cachedDataLayer = null;
    private String machineName;
    private String instanceName;
    private DbInstance dbInstanceConfiguration;
    private List<DbConfiguration> dbConfiguration;

    private Configuration() throws Exception {
        initialiseDataLayer();
        initialiseMachineName();
        retrieveInstanceName();
        //addHL7LogAppender();
        loadDbConfiguration();
    }

    public String getMachineName() {
        return machineName;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public List<DbConfiguration> getConfigurations() {
        return this.dbConfiguration;
    }

    public DbInstance getInstanceConfiguration() {
        return this.dbInstanceConfiguration;
    }

    private void initialiseMachineName() throws SftpReaderException {
        try {
            machineName = Hostname.getHostname();
        } catch (Exception e) {
            throw new SftpReaderException("Error getting machine name", e);
        }
    }

    private void retrieveInstanceName() throws SftpReaderException {

        this.instanceName = ConfigManager.getAppSubId();

        if (StringUtils.isEmpty(this.instanceName)) {
            throw new SftpReaderException("Applicaiton instance name not set (should be passed in as application argument)");
        }
    }

    private void loadDbConfiguration() throws Exception {

        DataLayerI dataLayer = getDataLayer();
        this.dbInstanceConfiguration = dataLayer.getInstanceConfiguration(this.instanceName, this.machineName);

        this.dbConfiguration = new ArrayList<>();

        for (String configurationId : this.dbInstanceConfiguration.getConfigurationIds()) {
            DbConfiguration configuration = dataLayer.getConfiguration(configurationId);
            this.dbConfiguration.add(configuration);
        }
    }

    public DataLayerI getDataLayer() throws Exception {
        return cachedDataLayer;
    }

    /*public DataSource getNonPooledDatabaseConnection() throws SQLException {
        return PgDataSource.get(dbUrl, dbUsername, dbPassword);
    }*/

    public DbConfiguration getConfiguration(String configurationId) throws SftpReaderException {
        List<DbConfiguration> dbConfigurations = this.dbConfiguration
                .stream()
                .filter(t -> t.getConfigurationId().equals(configurationId))
                .collect(Collectors.toList());

        if (dbConfigurations.size() == 0)
            throw new SftpReaderException("Could not find configuration with id " + configurationId);

        if (dbConfigurations.size() > 1)
            throw new SftpReaderException("Multiple configurations found with id " + configurationId);

        return dbConfigurations.get(0);
    }

    public String getConfigurationIdsForDisplay() {
        String commaSeperatedString = StringUtils.join(this.getInstanceConfiguration().getConfigurationIds(), ", ");
        return StringHelper.replaceLast(commaSeperatedString, ",", " and");
    }

    private synchronized void initialiseDataLayer() throws Exception {

        //VHP port: the SFTP Reader database comes from the 'database' config row (SftpReaderDb), not the
        //library's 'db_sftp_reader'. It is MySQL only - SftpReaderDb refuses any other JDBC url, which replaces
        //the old ConnectionManager.isPostgreSQL() check (that needed the PostgreSQL driver on the classpath).
        //Open a connection now so a wrong or missing config fails here, at start-up, as it did before.
        Connection connection = SftpReaderDb.getConnection();
        connection.close();

        this.cachedDataLayer = new MySqlDataLayer();

    }
}
