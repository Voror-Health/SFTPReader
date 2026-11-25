package org.endeavourhealth.sftpreader.implementations.imperial;

import org.endeavourhealth.sftpreader.implementations.SftpBulkDetector;
import org.endeavourhealth.sftpreader.implementations.imperial.utility.ImperialConstants;
import org.endeavourhealth.sftpreader.implementations.imperial.utility.ImperialHelper;
import org.endeavourhealth.sftpreader.model.DataLayerI;
import org.endeavourhealth.sftpreader.model.db.Batch;
import org.endeavourhealth.sftpreader.model.db.BatchSplit;
import org.endeavourhealth.sftpreader.model.db.DbConfiguration;
import org.endeavourhealth.sftpreader.model.db.DbInstanceEds;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ImperialBulkDetector extends SftpBulkDetector {

    /**
     * we've never received a single bulk for imperial, and don't expect to get one in the future, so no need to implement this
     */
    @Override
    public boolean isBulkExtract(Batch batch, BatchSplit batchSplit, DataLayerI db,
                                 DbInstanceEds instanceConfiguration, DbConfiguration dbConfiguration) throws Exception {
        return false;
    }

    /**
     * for imperial, check the key files in the CDE data only. This does mean we could fail to detect patient data if the CDE
     * files are empty and we have only CDS files, but I don't think that's possible
     */
    @Override
    public boolean hasPatientData(Batch batch, BatchSplit batchSplit, DataLayerI db, DbInstanceEds instanceConfiguration, DbConfiguration dbConfiguration) throws Exception {

        Set<String> fileTypeIds = new HashSet<>();
        fileTypeIds.add(ImperialConstants.FILE_ID_CDE_PPATI);
        fileTypeIds.add(ImperialConstants.FILE_ID_CDE_CLEVE);
        fileTypeIds.add(ImperialConstants.FILE_ID_CDE_ENCNT);

        for (String fileTypeId: fileTypeIds) {

            List<String> paths = ImperialHelper.findFilesInPermDir(instanceConfiguration, dbConfiguration, batch, fileTypeId);
            boolean isEmpty = isAllFilesEmpty(paths, ImperialConstants.CDE_CSV_FORMAT.withHeader());
            if (!isEmpty) {
                return true;
            }
        }

        return false;
    }
}
