package org.endeavourhealth.sftpreader.implementations.imperial;

import org.endeavourhealth.sftpreader.model.DataLayerI;

import org.endeavourhealth.sftpreader.implementations.SftpNotificationCreator;
import org.endeavourhealth.sftpreader.model.db.*;
import org.endeavourhealth.sftpreader.utilities.ExchangePayloadFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ImperialNotificationCreator extends SftpNotificationCreator {
    private static final Logger LOG = LoggerFactory.getLogger(ImperialNotificationCreator.class);

    @Override
    public PayloadWrapper createNotificationMessage(String organisationId, DataLayerI db, DbInstanceEds instanceConfiguration,
                                            DbConfiguration dbConfiguration, BatchSplit batchSplit) throws Exception {


        List<ExchangePayloadFile> files = super.findFilesForDefaultNotificationMessage(instanceConfiguration, dbConfiguration, db, batchSplit, null);

        //the 2.1 and 2.2 files have different naming systems, but if we simply compare on any numeric element
        //of the file names, we get a consistent ordering that works for both
        List<FileWrapper> fileWrappers = new ArrayList<>();
        for (ExchangePayloadFile file: files) {

            //imperial send us zero-length files when there's no content, rather than files with
            //the headers present but no further records. This causes problems with CSV parsing, as
            //the lack of headers is flagged as an error. To avoid this, don't send over any zero-length files
            if (file.getSize().longValue() == 0) {
                continue;
            }

            fileWrappers.add(new FileWrapper(file));
        }

        Collections.sort(fileWrappers);

        files = new ArrayList<>();
        for (FileWrapper fileWrapper: fileWrappers) {
            files.add(fileWrapper.getFileObj());
        }

        return new PayloadWrapper(files);
    }


    static class FileWrapper implements Comparable {
        private ExchangePayloadFile fileObj = null;
        private List<Long> numericElements = null;

        public FileWrapper(ExchangePayloadFile fileObj) {
            this.fileObj = fileObj;

            //parse out any numeric elements
            this.numericElements = new ArrayList<>();

            String fileName = fileObj.getPath();
            String[] toks = fileName.split("[._-]"); //split by dot, underscore and dash
            for (String tok: toks) {
                try {
                    Long l = Long.valueOf(tok);
                    this.numericElements.add(l);

                } catch (NumberFormatException nfe) {
                    //skip it
                }
            }
        }

        public ExchangePayloadFile getFileObj() {
            return fileObj;
        }

        @Override
        public int compareTo(Object o) {
            if (o instanceof FileWrapper) {
                List<Long> ourElements = this.numericElements;
                List<Long> otherElements = ((FileWrapper)o).numericElements;
                if (ourElements.size() > otherElements.size()) {
                    return 1;

                } else if (ourElements.size() < otherElements.size()) {
                    return -1;

                } else {
                    for (int i=0; i<ourElements.size(); i++) {
                        Long ourElement = ourElements.get(i);
                        Long otherElement = otherElements.get(i);
                        int elementComp = ourElement.compareTo(otherElement);
                        if (elementComp != 0) {
                            return elementComp;
                        }
                    }
                }

            }
            return 0;
        }
    }
}
