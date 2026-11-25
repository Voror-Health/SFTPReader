package org.endeavourhealth.sftpreader.implementations.nems;

import org.apache.commons.io.FilenameUtils;
import org.endeavourhealth.common.utility.FileHelper;
import org.endeavourhealth.common.utility.SlackHelper;
import org.endeavourhealth.sftpreader.implementations.SftpFilenameParser;
import org.endeavourhealth.sftpreader.model.db.DbConfiguration;
import org.endeavourhealth.sftpreader.model.exceptions.SftpFilenameParseException;
import org.endeavourhealth.sftpreader.utilities.RemoteFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import static org.apache.commons.io.FilenameUtils.getBaseName;
import static org.endeavourhealth.sftpreader.implementations.nems.utility.NemsConstants.*;

public class NemsFilenameParser extends SftpFilenameParser {
    private static final Logger LOG = LoggerFactory.getLogger(NemsFilenameParser.class);
    // same as ISO pattern but switch : for . so can be used as filename
    private static final DateTimeFormatter BATCH_IDENTIFIER_FORMAT = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);

    private LocalDateTime extractDateTime;
    private String fileTypeIdentifier;
    private boolean isFileNeeded;

    public NemsFilenameParser(boolean isRawFile, RemoteFile remoteFile, DbConfiguration dbConfiguration) {
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
        return this.fileTypeIdentifier;
    }

    @Override
    public boolean isFileNeeded() {
        return isFileNeeded;
    }

    @Override
    public boolean ignoreUnknownFileTypes() {
        return true;  //to ignore the .csv as part of test
    }

    @Override
    protected void parseFilename(boolean isRawFile) throws SftpFilenameParseException {
        String fileName = this.remoteFile.getFilename();
        //this will be either a data or control file (.dat or .ctl)
        String ext = FilenameUtils.getExtension(fileName);
        if (!ext.equalsIgnoreCase("dat") && !ext.equalsIgnoreCase("ctl")) {
            throw new SftpFilenameParseException("Unexpected file extension (" + ext + ") for filename: " + fileName);
        }

        //check for rogue characters in filena////me
        String fullPath = this.remoteFile.getFullPath();
        String baseName = getBaseName(fullPath);
        if (baseName.contains("%") || fullPath.contains("/\\")) {

            throw new SftpFilenameParseException("Unexpected filename format found: " + fullPath);
        }

        //filename format from specification
        //e.g. 20230221144313365818_B46B90.ctl or 20230221144313365818_B46B90.dat
        if (ext.equalsIgnoreCase("ctl")) {
            this.isFileNeeded = false;
        } else if (ext.equalsIgnoreCase("dat")) {
            getFileTypeIdentifierFromFilePath(fullPath);
            String dateTimeStr = baseName.substring(0, 14);
            this.extractDateTime = LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            this.isFileNeeded = true;
        } else {
            throw new RuntimeException("Error file provided did not have a ctl to provide type");
        }
    }

    private void getFileTypeIdentifierFromFilePath(String fullPath) throws SftpFilenameParseException{
        if(fullPath.contains("BIRTHNOTIFICATION_1")){
            this.fileTypeIdentifier = FILE_ID_BIRTH_NOTIFICATION;
        } else if(fullPath.contains("DEATHNOTIFICATION_1")){
            this.fileTypeIdentifier = FILE_ID_DEATH_NOTIFICATION;
        } else {
            throw new SftpFilenameParseException("NEMS filepath: " + fullPath +
                    ", does not contain the birth or death notification identifier.");
        }
        //Old solution below, will (probably) never need it again, but its here in case.
        /*boolean keepRunning = true;
        try {
            InputStreamReader stream = FileHelper.readFileReaderFromSharedStorage(fullPath);
            BufferedReader reader = new BufferedReader(stream);
            String line = reader.readLine();
            while (line != null) {
                if(keepRunning) {
                    if (line.contains(FILE_ID_DEATH_NOTIFICATION)) {
                        this.fileTypeIdentifier = FILE_ID_DEATH_NOTIFICATION;
                        keepRunning = false;
                    } else if (line.contains(FILE_ID_BIRTH_NOTIFICATION)) {
                        this.fileTypeIdentifier = FILE_ID_BIRTH_NOTIFICATION;
                        keepRunning = false;
                    }
                }
                line = reader.readLine();
            }
            reader.close();
            stream.close();
        } catch (IOException e) {
            throw new RuntimeException("There was an io error attempting to read: " + fullPath, e);
        } catch (NullPointerException e) {
            throw new RuntimeException("Null pointer exception found for ctl file", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }*/
    }

    @Override
    public Date getExtractDate() {
        return Date.from(extractDateTime
                .atZone(ZoneId.systemDefault())
                .toInstant());
    }
}