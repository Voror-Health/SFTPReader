package org.endeavourhealth.sftpreader.implementations.imperial;

import org.endeavourhealth.sftpreader.implementations.imperial.utility.ImperialConstants;
import org.endeavourhealth.sftpreader.model.DataLayerI;

import org.endeavourhealth.sftpreader.implementations.SftpBatchSplitter;
import org.endeavourhealth.sftpreader.model.db.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ImperialBatchSplitter extends SftpBatchSplitter {
    private static final Logger LOG = LoggerFactory.getLogger(ImperialBatchSplitter.class);

    @Override
    public List<BatchSplit> splitBatch(Batch batch, Batch lastCompleteBatch, DataLayerI db, DbInstanceEds instanceConfiguration, DbConfiguration dbConfiguration) throws Exception {

        //create a single batch split and return it
        List<BatchSplit> ret = new ArrayList<>();

        BatchSplit batchSplit = new BatchSplit();
        batchSplit.setBatchId(batch.getBatchId());
        batchSplit.setLocalRelativePath(batch.getLocalRelativePath());
        batchSplit.setOrganisationId(ImperialConstants.ICH_ODS_CODE);

        ret.add(batchSplit);

        return ret;
    }
}
