package org.endeavourhealth.sftpreader.implementations.emis;

import com.google.common.base.Strings;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.endeavourhealth.sftpreader.implementations.SftpFilenameParser;
import org.endeavourhealth.sftpreader.implementations.emis.utility.ProcessingIdSet;
import org.endeavourhealth.sftpreader.model.db.BatchFile;
import org.endeavourhealth.sftpreader.model.db.DbConfiguration;
import org.endeavourhealth.sftpreader.model.exceptions.SftpFilenameParseException;
import org.endeavourhealth.sftpreader.utilities.RemoteFile;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class EmisFilenameParser extends SftpFilenameParser {
                                                                        // same as ISO pattern but switch : for . so can be used as filename and sorted
    private static final DateTimeFormatter BATCH_IDENTIFIER_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH'.'mm'.'ss");

    private ProcessingIdSet processingIds;
    private String fileTypeIdentifier;
    private LocalDateTime extractDateTime;

    /*public EmisSftpFilenameParser(String filename, DbConfiguration dbConfiguration, String fileExtension) {
        super(filename, dbConfiguration, fileExtension);
    }*/

    public EmisFilenameParser(boolean isRawFile, RemoteFile remoteFile, DbConfiguration dbConfiguration) {
        super(isRawFile, remoteFile, dbConfiguration);
    }

    @Override
    public String generateBatchIdentifier() {
        return this.extractDateTime.format(BATCH_IDENTIFIER_FORMAT);
    }

    public static LocalDateTime parseBatchIdentifier(String batchIdentifier) {
        return LocalDateTime.parse(batchIdentifier, BATCH_IDENTIFIER_FORMAT);
    }

    @Override
    public String generateFileTypeIdentifier() {
        return fileTypeIdentifier;
    }

    @Override
    public boolean isFileNeeded(){
        return true;
    }

    @Override
    public boolean ignoreUnknownFileTypes() {
        return false;
    }


    @Override
    protected void parseFilename(boolean isRawFile) throws SftpFilenameParseException {

        String processingIdPart = null;
        String schemaNamePart = null;
        String tableNamePart = null;
        String extractDateTimePart = null;

        String fileName = this.remoteFile.getFilename();
        String[] parts = fileName.split("_");

        //SD-374 with Emis moving to AWS they're changing the file name format to include an extra token
        if (parts.length == 5) {
            //the older format
            processingIdPart = parts[0];
            schemaNamePart = parts[1];
            tableNamePart = parts[2];
            extractDateTimePart = parts[3];
            //sharingAgreementGuidWithFileExtensionPart = parts[4]; //not used here

        } else if (parts.length == 6) {
            //newer format
            //bulkIndicator = parts[0]; //bulk/delta indicator, but not useful as still says delta when new bulks are added to existing extract
            processingIdPart = parts[1];
            schemaNamePart = parts[2];
            tableNamePart = parts[3];
            extractDateTimePart = parts[4];
            //sharingAgreementGuidWithFileExtensionPart = parts[5]; //not used here

        } else {
            throw new SftpFilenameParseException("Emis filename [" + fileName + "] could not be parsed");
        }


        this.processingIds = ProcessingIdSet.parseProcessingIdRangeFromFileName(processingIdPart);

        if (StringUtils.isEmpty(schemaNamePart)) {
            throw new SftpFilenameParseException("Schema name is empty");
        }

        if (StringUtils.isEmpty(tableNamePart)) {
            throw new SftpFilenameParseException("Table name is empty");
        }

        if (StringUtils.isEmpty(extractDateTimePart)) {
            throw new SftpFilenameParseException("Extract date/time is empty");
        }

        this.fileTypeIdentifier = schemaNamePart + "_" + tableNamePart;

        this.extractDateTime = LocalDateTime.parse(extractDateTimePart, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }


    public ProcessingIdSet getProcessingIds() {
        return this.processingIds;
    }

    public LocalDateTime getExtractDateTime() {
        return extractDateTime;
    }

    public static String getDecryptedFileName(BatchFile batchFile) {
        return getDecryptedFileName(batchFile.getFilename());
    }

    /**
     * the encrypted files have the same name AND extension as the decrypted ones PLUS the gpg extension
     * e.g. 291_Agreements_SharingOrganisation_20150211164536_45E7CD20-EE37-41AB-90D6-DC9D4B03D102.csv.gpg
     * so the decrypted filename is simply the filename with the gpg extension removed
     */
    public static String getDecryptedFileName(String encryptedFilename) {

        String extension = FilenameUtils.getExtension(encryptedFilename);
        if (extension.equalsIgnoreCase("gpg")) {
            //if the original file name is a GPG file, then just trim that off to get the decrypted file name
            return FilenameUtils.removeExtension(encryptedFilename);

        } else if (extension.equalsIgnoreCase("csv")) {
            //if the original file was an unencrypted CSV (like we use on some pre-production servers) then just return the filename as is
            return encryptedFilename;

        } else {
            throw new RuntimeException("Unexpected file extension on " + encryptedFilename);
        }
    }

    @Override
    public Date getExtractDate() {
        return java.util.Date.from(extractDateTime
                .atZone(ZoneId.systemDefault())
                .toInstant());
    }
}
