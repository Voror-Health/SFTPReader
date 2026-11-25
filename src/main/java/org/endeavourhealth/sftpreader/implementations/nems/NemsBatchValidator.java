package org.endeavourhealth.sftpreader.implementations.nems;

import org.apache.commons.lang3.Validate;
import org.endeavourhealth.sftpreader.implementations.SftpBatchValidator;
import org.endeavourhealth.sftpreader.model.DataLayerI;
import org.endeavourhealth.sftpreader.model.db.Batch;
import org.endeavourhealth.sftpreader.model.db.DbConfiguration;
import org.endeavourhealth.sftpreader.model.db.DbInstanceEds;
import org.endeavourhealth.sftpreader.model.exceptions.SftpValidationException;

public class NemsBatchValidator extends SftpBatchValidator {
    @Override
    public boolean validateBatch(Batch incompleteBatch, Batch lastCompleteBatch, DbInstanceEds instanceConfiguration, DbConfiguration dbConfiguration, DataLayerI db) throws SftpValidationException {

        Validate.notNull(incompleteBatch, "incompleteBatch is null");
        Validate.notNull(dbConfiguration, "dbConfiguration is null");
        Validate.notNull(dbConfiguration.getInterfaceFileTypes(), "dbConfiguration.interfaceFileTypes is null");
        Validate.notEmpty(dbConfiguration.getInterfaceFileTypes(), "No interface file types configured");

        // Number of files in batch not checked
        //if (incompleteBatch.getBatchFiles().size() != 1) {
        //  throw new SftpValidationException("Incorrect number of files in batch. Batch identifier = " + incompleteBatch.getBatchIdentifier());
        //}

        return true;
    }
}
