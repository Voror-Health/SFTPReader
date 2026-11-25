package org.endeavourhealth.sftpreader.implementations.imperial;

import org.apache.commons.csv.CSVFormat;
import org.endeavourhealth.sftpreader.implementations.SftpBatchDateDetector;
import org.endeavourhealth.sftpreader.implementations.imperial.utility.ImperialConstants;
import org.endeavourhealth.sftpreader.implementations.imperial.utility.ImperialHelper;
import org.endeavourhealth.sftpreader.model.DataLayerI;
import org.endeavourhealth.sftpreader.model.db.Batch;
import org.endeavourhealth.sftpreader.model.db.DbConfiguration;
import org.endeavourhealth.sftpreader.model.db.DbInstanceEds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.*;

public class ImperialDateDetector extends SftpBatchDateDetector {
    private static final Logger LOG = LoggerFactory.getLogger(ImperialDateDetector.class);

    @Override
    public Date detectExtractDate(Batch batch, DataLayerI db, DbInstanceEds instanceConfiguration, DbConfiguration dbConfiguration) throws Exception {

        //because of the mess of CDE and CDS files, we only use a DATE for the imperial batch identifer, so shouldn't just
        //use that as the extract date. For the purposes of simplicity, we look for the CDE PPATI file(s) and
        //find the extract date field from that file
        List<String> fileTypes = new ArrayList<>();
        fileTypes.add(ImperialConstants.FILE_ID_CDE_PPATI);
        fileTypes.add(ImperialConstants.FILE_ID_CDE_ENCNT);
        fileTypes.add(ImperialConstants.FILE_ID_CDE_CVREF);

        for (String fileType: fileTypes) {

            List<String> filePaths = ImperialHelper.findFilesInPermDir(instanceConfiguration, dbConfiguration, batch, fileType);
            CSVFormat csvFormat = ImperialConstants.CDE_CSV_FORMAT.withHeader();

            //all CDE files in the same batch will have the same extract date, so as soon as we find one file, just use that and don't look at any others
            for (String filePath : filePaths) {

                Date fileDate = null;
                try {
                    fileDate = findLatestDateFromFile(filePath, csvFormat, ImperialConstants.CDE_DATE_TIME_FORMAT, "EXTRACT_DT_TM");
                } catch (ParseException pe) {
                    LOG.error("Error parsing date " + pe.getMessage());
                    LOG.error("Will attempt using imperial bulk date time format");
                    fileDate = findLatestDateFromFile(filePath, csvFormat, ImperialConstants.CDE_BULK_DATE_TIME_FORMAT, "EXTRACT_DT_TM");
                }

                if (fileDate != null) {
                    return fileDate;
                }
            }
        }

        LOG.error("Failed to find extract date in batch " + batch.getBatchId());
        return null;
    }

    @Override
    public Date detectExtractCutoff(Batch batch, DataLayerI db, DbInstanceEds instanceConfiguration, DbConfiguration dbConfiguration) throws Exception {

        //we believe that the PowerInsight data warehouse that is used for the CDE extracts is only updated at midnight, so
        //the data cutoff is the midnight before the extract date
        Date extractDate = batch.getExtractDate(); //this will already have been set if possible
        if (extractDate == null) {
            return null;
        }

        Calendar cal = Calendar.getInstance();
        cal.setTime(extractDate);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        return cal.getTime();
    }

}
