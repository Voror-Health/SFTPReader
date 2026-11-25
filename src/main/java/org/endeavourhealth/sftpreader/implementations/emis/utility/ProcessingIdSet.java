package org.endeavourhealth.sftpreader.implementations.emis.utility;

import org.apache.commons.lang3.StringUtils;
import org.endeavourhealth.sftpreader.model.exceptions.SftpFilenameParseException;

public class ProcessingIdSet {
    private int processingIdStart;
    private int processingIdEnd;

    public ProcessingIdSet(int processingIdStart, int processingIdEnd) {
        this.processingIdStart = processingIdStart;
        this.processingIdEnd = processingIdEnd;
    }

    public int getProcessingIdStart() {
        return processingIdStart;
    }

    public int getProcessingIdEnd() {
        return processingIdEnd;
    }

    public String getBatchIdentifier() {
        return Integer.toString(getProcessingIdStart()) + "-" + Integer.toString(getProcessingIdEnd());
    }

    /**
     * the start of the filename contains either a single processing ID integer, or an ID range, with a dash between the ints
     */
    public static ProcessingIdSet parseProcessingIdRangeFromFileName(String batchIdentifier) throws SftpFilenameParseException {
        if (StringUtils.isBlank(batchIdentifier)) {
            throw new SftpFilenameParseException("No processing ids present");
        }

        String[] processingIdParts = batchIdentifier.split("-");

        int processingIdStart = Integer.parseInt(processingIdParts[0]);
        int processingIdEnd = -1;

        if (processingIdParts.length == 1) {
            //if only one int, then start and end are the same
            processingIdEnd = processingIdStart;

        } else if (processingIdParts.length == 2) {
            //if two ints, then end is the second token
            processingIdEnd = Integer.parseInt(processingIdParts[1]);

        } else if (processingIdParts.length > 2) {
            throw new SftpFilenameParseException("Too many processing ids");
        }

        if (processingIdStart > processingIdEnd) {
            throw new SftpFilenameParseException("Processing id start is greater than processing id end");
        }

        if (processingIdStart < 0) {
            throw new SftpFilenameParseException("Processing id start is less than zero");
        }

        if (processingIdEnd < 0) {
            throw new SftpFilenameParseException("Processing id end is less than zero");
        }

        return new ProcessingIdSet(processingIdStart, processingIdEnd);
    }
}
