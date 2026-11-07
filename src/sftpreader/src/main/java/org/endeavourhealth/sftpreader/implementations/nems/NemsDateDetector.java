package org.endeavourhealth.sftpreader.implementations.nems;

import org.endeavourhealth.sftpreader.implementations.SftpBatchDateDetector;
import org.endeavourhealth.sftpreader.model.DataLayerI;
import org.endeavourhealth.sftpreader.model.db.Batch;
import org.endeavourhealth.sftpreader.model.db.DbConfiguration;
import org.endeavourhealth.sftpreader.model.db.DbInstanceEds;

import java.time.LocalDateTime;
import java.util.Date;

public class NemsDateDetector extends SftpBatchDateDetector {
    @Override
    public Date detectExtractDate(Batch batch, DataLayerI db, DbInstanceEds instanceConfiguration, DbConfiguration dbConfiguration) throws Exception {
        String batchIdentifier = batch.getBatchIdentifier();
        LocalDateTime localDateTime = NemsFilenameParser.parseBatchIdentifier(batchIdentifier);
        return java.sql.Timestamp.valueOf(localDateTime);
    }

    @Override
    public Date detectExtractCutoff(Batch batch, DataLayerI db, DbInstanceEds instanceConfiguration, DbConfiguration dbConfiguration) throws Exception {
        return detectExtractDate(batch, db, instanceConfiguration, dbConfiguration);
    }
}
