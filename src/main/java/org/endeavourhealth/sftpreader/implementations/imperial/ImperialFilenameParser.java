package org.endeavourhealth.sftpreader.implementations.imperial;

import org.apache.commons.io.FilenameUtils;
import org.endeavourhealth.sftpreader.implementations.SftpFilenameParser;
import org.endeavourhealth.sftpreader.implementations.imperial.utility.ImperialConstants;
import org.endeavourhealth.sftpreader.model.db.DbConfiguration;
import org.endeavourhealth.sftpreader.model.exceptions.SftpFilenameParseException;
import org.endeavourhealth.sftpreader.utilities.RemoteFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class ImperialFilenameParser extends SftpFilenameParser {

    private static final DateTimeFormatter BATCH_IDENTIFIER_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    //special constants for the non-standard files, but we just use the first part of the file name for 2.2 files
    public static final String TYPE_2_1_SUSOPA = "SusOutpatient";
    public static final String TYPE_2_1_SUSAEA = "SusEmergency";
    public static final String TYPE_2_1_SUSIP = "SusInpatient";
    public static final String TYPE_2_1_TAILOPA = "SusOutpatientTail";
    public static final String TYPE_2_1_TAILAEA = "SusEmergencyTail";
    public static final String TYPE_2_1_TAILIP = "SusInpatientTail";
    public static final String TYPE_2_1_PROC = "Procedure";
    public static final String TYPE_2_1_DIAG = "Diagnosis";
    public static final String TYPE_2_1_BIRTH = "MaternityBirth";
    public static final String TYPE_2_1_PREG = "Pregnancy";
    public static final String TYPE_2_1_PROB = "Problem";
    public static final String TYPE_2_2_SURGINET = "SurginetCaseInfo";
    public static final String TYPE_2_2_CC = "CriticalCare"; //Critial Care
    public static final String TYPE_2_2_HDB = "HomeDeliveryAndBirth"; //Home Delivery and Birth
    public static final String TYPE_2_2_FAMILY_HISTORY = "FamilyHistory";
    public static final String TYPE_EMERGENCY_CARE = "SusEmergencyCareDataSet";
    public static final String TYPE_EMERGENCY_CARE_TAILS = "SusEmergencyCareDataSetTail";
    public static final String TYPE_APPOINTMENT_SCHEDULING = "APPSL2";
    public static final String TYPE_EVENT_CODE = "EventCode";
    public static final String TYPE_EVENT_SET_CANON = "EventSetCanon";
    public static final String TYPE_EVENT_SET = "EventSet";
    public static final String TYPE_EVENT_SET_EXPLODE = "EventSetExplode";
    public static final String TYPE_BLOB_CONTENT = "BlobContent";
    public static final String TYPE_MATERNITY_SERVICES_DATA_SET = "MaternityServicesDataSet";

    private String fileTypeIdentifier;
    private LocalDate extractDate;
    private boolean isFileNeeded;

    public ImperialFilenameParser(boolean isRawFile, RemoteFile remoteFile, DbConfiguration dbConfiguration) {
        super(isRawFile, remoteFile, dbConfiguration);
    }

    @Override
    public String generateBatchIdentifier() {
        //batch the extract files by date
        return createBatchIdentifier(extractDate);
    }

    @Override
    public String generateFileTypeIdentifier() {
        return fileTypeIdentifier;
    }

    @Override
    public boolean isFileNeeded(){
        return isFileNeeded;
    }

    @Override
    public boolean ignoreUnknownFileTypes() {
        //changing to false for 2.2 so as we now should know what all the files mean
        return false;
        //return true;
    }

    public static LocalDate parseBatchIdentifier(String batchIdentifier) {
        return LocalDate.parse(batchIdentifier, BATCH_IDENTIFIER_FORMAT);
    }

    public static String createBatchIdentifier(LocalDate localDate) {
        return BATCH_IDENTIFIER_FORMAT.format(localDate);
    }

    @Override
    protected void parseFilename(boolean isRawFile) throws SftpFilenameParseException {

        //by default we want ALL files
        isFileNeeded = true;

        String fileName = this.remoteFile.getFilename();
        LocalDateTime lastModified = this.remoteFile.getLastModified();

        //we get partial files with extension file part, which are artifacts of copying to our SFTP server, so ignore them
        String ext = FilenameUtils.getExtension(fileName);
        if (ext.equalsIgnoreCase("filepart")) {
            isFileNeeded = false;
            return;
        }

        String baseName = FilenameUtils.getBaseName(fileName); //name without extension
        String[] toks = baseName.split("_");

        String tok1 = toks[0];

        if (tok1.equalsIgnoreCase("susopa")) {
            fileTypeIdentifier = TYPE_2_1_SUSOPA;
            extractDate = lastModified.toLocalDate(); //filename doesn't have the date, so use the modified date

        } else if (tok1.equalsIgnoreCase("susaea")) {
            fileTypeIdentifier = TYPE_2_1_SUSAEA;
            extractDate = lastModified.toLocalDate(); //filename doesn't have the date, so use the modified date

        } else if (tok1.equalsIgnoreCase("ip")) {
            fileTypeIdentifier = TYPE_2_1_SUSIP;
            extractDate = lastModified.toLocalDate(); //filename doesn't have the date, so use the modified date

        } else if (tok1.equalsIgnoreCase("tailopa")) {
            fileTypeIdentifier = TYPE_2_1_TAILOPA;
            extractDate = lastModified.toLocalDate(); //filename doesn't have the date, so use the modified date

        } else if (tok1.equalsIgnoreCase("tailaea")) {
            fileTypeIdentifier = TYPE_2_1_TAILAEA;
            extractDate = lastModified.toLocalDate(); //filename doesn't have the date, so use the modified date

        } else if (tok1.equalsIgnoreCase("tailip")) {
            fileTypeIdentifier = TYPE_2_1_TAILIP;
            extractDate = lastModified.toLocalDate(); //filename doesn't have the date, so use the modified date

        } else if (tok1.equalsIgnoreCase("cc")) {
            fileTypeIdentifier = TYPE_2_2_CC;
            extractDate = lastModified.toLocalDate(); //filename doesn't have the date, so use the modified date

        } else if (tok1.equalsIgnoreCase("hdb")) {
            fileTypeIdentifier = TYPE_2_2_HDB;
            extractDate = lastModified.toLocalDate(); //filename doesn't have the date, so use the modified date

        } else if (tok1.equalsIgnoreCase("susecd")) {
            fileTypeIdentifier = TYPE_EMERGENCY_CARE;
            extractDate = lastModified.toLocalDate(); //filename doesn't have the date, so use the modified date

        } else if (tok1.equalsIgnoreCase("tailecd")) {
            fileTypeIdentifier = TYPE_EMERGENCY_CARE_TAILS;
            extractDate = lastModified.toLocalDate(); //filename doesn't have the date, so use the modified date

        } else if (tok1.equalsIgnoreCase("eve")) {
            String tok2 = toks[1];
            if (tok2.equalsIgnoreCase("cano")) {
                fileTypeIdentifier = TYPE_EVENT_SET_CANON;

            } else if (tok2.equalsIgnoreCase("code")) {
                fileTypeIdentifier = TYPE_EVENT_CODE;

            } else if (tok2.equalsIgnoreCase("set")) {
                fileTypeIdentifier = TYPE_EVENT_SET;

            } else if (tok2.equalsIgnoreCase("expl")) {
                fileTypeIdentifier = TYPE_EVENT_SET_EXPLODE;

            } else {
                throw new SftpFilenameParseException("Unsupported token [" + tok2 + "] after [" + tok1 + "]");
            }

            String tok3 = toks[2];
            extractDate = LocalDate.parse(tok3, DateTimeFormatter.ofPattern("yyyyMMdd"));

        } else if (tok1.equalsIgnoreCase("blob")) {
            String tok2 = toks[1];
            if (tok2.equalsIgnoreCase("con")) {
                fileTypeIdentifier = TYPE_BLOB_CONTENT;

            } else {
                throw new SftpFilenameParseException("Unsupported token [" + tok2 + "] after [" + tok1 + "]");
            }

            String tok3 = toks[2];
            extractDate = LocalDate.parse(tok3, DateTimeFormatter.ofPattern("yyyyMMdd"));

        } else if (tok1.equalsIgnoreCase("spfit")) {
            fileTypeIdentifier = TYPE_2_2_SURGINET;
            if (toks.length != 6) {
                throw new SftpFilenameParseException("Expecting six elements in filename [" + fileName + "]");
            }

            String tok6 = toks[5].substring(0, 8);
            extractDate = LocalDate.parse(tok6, DateTimeFormatter.ofPattern("yyyyMMdd"));

        } else if (tok1.equalsIgnoreCase("ryj")) {
            if (toks.length != 4) {
                throw new SftpFilenameParseException("Expecting four elements in filename starting ryj [" + fileName + "]");
            }

            String tok3 = toks[2];
            if (tok3.equalsIgnoreCase("proce")) {
                fileTypeIdentifier = TYPE_2_1_PROC;

            } else if (tok3.equalsIgnoreCase("probe")) {
                fileTypeIdentifier = TYPE_2_1_PROB;

            } else if (tok3.equalsIgnoreCase("diagn")) {
                fileTypeIdentifier = TYPE_2_1_DIAG;

            } else {
                throw new SftpFilenameParseException("Unexpected third element " + tok3 + " after ryj in filename [" + fileName + "]");
            }

            String tok4 = toks[3].substring(0, 8);
            extractDate = LocalDate.parse(tok4, DateTimeFormatter.ofPattern("yyyyMMdd"));

        } else if (tok1.equalsIgnoreCase("GETL")) {
            if (toks.length == 6) {
                String tok3 = toks[2];
                if (tok3.equalsIgnoreCase("PREG")) {
                    fileTypeIdentifier = TYPE_2_1_PREG;

                } else if (tok3.equalsIgnoreCase("BIRTH")) {
                    fileTypeIdentifier = TYPE_2_1_BIRTH;

                } else {
                    throw new SftpFilenameParseException("Unexpected third element " + tok3 + " after GETL in filename [" + fileName + "]");
                }

                String tok4 = toks[3].substring(0, 10);
                extractDate = LocalDate.parse(tok4, DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            } else if (toks.length == 7) {
                String tok2 = toks[1];
                if (tok2.equals("APPSL2")) {
                    fileTypeIdentifier = TYPE_APPOINTMENT_SCHEDULING;

                    String tok5 = toks[4];
                    extractDate = LocalDate.parse(tok5, DateTimeFormatter.ofPattern("ddMMyyyy"));
                } else {
                    throw new SftpFilenameParseException("Unexpected second element " + tok2 + " after GETL in filename [" + fileName + "]");
                }

            } else {
                throw new SftpFilenameParseException("Unexpected number of elements after GETL in filename [" + fileName + "]");
            }

        } else if (tok1.equalsIgnoreCase("Fam")) {
            if (toks.length != 4) {
                throw new SftpFilenameParseException("Expecting four elements in filename starting Fam [" + fileName + "]");
            }

            String tok2 = toks[1];
            if (!tok2.equalsIgnoreCase("Hist")) {
                throw new SftpFilenameParseException("Expecting Hist elements in filename starting Fam [" + fileName + "]");
            }

            fileTypeIdentifier = TYPE_2_2_FAMILY_HISTORY;

            String tok3 = toks[2];
            extractDate = LocalDate.parse(tok3, DateTimeFormatter.ofPattern("yyyyMMdd"));

        } else if (tok1.startsWith("msds")) {
            String tok3 = toks[2];
            if (tok3.equals("msds")) {
                fileTypeIdentifier = TYPE_MATERNITY_SERVICES_DATA_SET;

                //the filename does contain a date, but we're not sent the file until weeks after this date,
                //so just use the file creation date as the batch date
                extractDate = lastModified.toLocalDate();

            } else {
                throw new SftpFilenameParseException("Expecting msds element in filename starting msds [" + fileName + "]");
            }

        } else {
            this.fileTypeIdentifier = tok1;

            if (toks.length < 4) {
                throw new SftpFilenameParseException("Expecting at least four elements in filename [" + fileName + "]");
            }

            //file date is either in the third or fourth element
            String tok3 = toks[2];
            if (tok3.equalsIgnoreCase(ImperialConstants.ICH_ODS_CODE)) {
        //    if (tok3.equalsIgnoreCase("RYJ")) {
                String tok4 = toks[3];
              //  extractDate = LocalDate.parse(tok4, DateTimeFormatter.ofPattern("ddMMyyyy"));
                extractDate = LocalDate.parse(tok4, DateTimeFormatter.ofPattern("yyyyMMdd"));

            } else {
              //  extractDate = LocalDate.parse(tok3, DateTimeFormatter.ofPattern("ddMMyyyy"));
                extractDate = LocalDate.parse(tok3, DateTimeFormatter.ofPattern("yyyyMMdd"));

            }
        }
    }

    @Override
    public Date getExtractDate() {
        return java.util.Date.from(extractDate.atStartOfDay()
                .atZone(ZoneId.systemDefault())
                .toInstant());
    }
}
