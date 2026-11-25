package org.endeavourhealth.sftpreader.implementations.nems;

import org.endeavourhealth.sftpreader.implementations.SftpBulkDetector;
import org.endeavourhealth.sftpreader.model.DataLayerI;
import org.endeavourhealth.sftpreader.model.db.*;

public class NemsBulkDetector extends SftpBulkDetector {
    @Override
    public boolean isBulkExtract(Batch batch, BatchSplit batchSplit, DataLayerI db, DbInstanceEds instanceConfiguration, DbConfiguration dbConfiguration) throws Exception {
        return false;
    }

    @Override
    public boolean hasPatientData(Batch batch, BatchSplit batchSplit, DataLayerI db, DbInstanceEds instanceConfiguration, DbConfiguration dbConfiguration) throws Exception {
        return true;
    }
}
