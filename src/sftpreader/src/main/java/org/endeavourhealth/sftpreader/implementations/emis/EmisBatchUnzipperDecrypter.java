package org.endeavourhealth.sftpreader.implementations.emis;

import com.google.common.base.Strings;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.endeavourhealth.common.utility.FileHelper;
import org.endeavourhealth.sftpreader.model.DataLayerI;

import org.endeavourhealth.sftpreader.implementations.SftpBatchUnzipperDecrypter;
import org.endeavourhealth.sftpreader.model.db.Batch;
import org.endeavourhealth.sftpreader.model.db.BatchFile;
import org.endeavourhealth.sftpreader.model.db.DbConfiguration;
import org.endeavourhealth.sftpreader.model.db.DbInstanceEds;
import org.endeavourhealth.sftpreader.utilities.PgpUtil;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public class EmisBatchUnzipperDecrypter extends SftpBatchUnzipperDecrypter {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(EmisBatchUnzipperDecrypter.class);

    @Override
    public void unzipAndDecrypt(Batch batch, DbInstanceEds instanceConfiguration, DbConfiguration dbConfiguration, DataLayerI db) throws Exception {

        //Emis files are PGP encrypted, so we need to decrypt them

        String tempRootDir = instanceConfiguration.getTempDirectory();
        String sharedStoragePath = instanceConfiguration.getSharedStoragePath();

        String configurationDir = dbConfiguration.getLocalRootPath();
        String batchDir = batch.getLocalRelativePath();

        String tempDir = FilenameUtils.concat(tempRootDir, configurationDir);
        tempDir = FilenameUtils.concat(tempDir, batchDir);

        String storageDir = FilenameUtils.concat(sharedStoragePath, configurationDir);
        storageDir = FilenameUtils.concat(storageDir, batchDir);

        //ensure the temp dir exists
        File f = new File(tempDir);
        if (!f.exists()) {
            f.mkdirs();
        }

        for (BatchFile batchFile: batch.getBatchFiles()) {

            String encryptedFilename = batchFile.getFilename();
            String encryptedSourceFile = FilenameUtils.concat(storageDir, encryptedFilename);

            InputStream inputStream = FileHelper.readFileFromSharedStorage(encryptedSourceFile);
            try {

                String extension = FilenameUtils.getExtension(encryptedFilename);
                if (extension.equalsIgnoreCase("csv")) {

                    //on some of the "transform" servers, we use already decrypted Emis data as the source, so
                    //we don't have any encryption config. In this case, simply copy the source file into temp
                    //so the outcome is the same as it being decrypted
                    String decryptedTempFile = FilenameUtils.concat(tempDir, encryptedFilename);

                    LOG.info("   Copying w/o decryption to: " + decryptedTempFile);
                    Path destination = new File(decryptedTempFile).toPath();
                    Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);

                } else if (extension.equalsIgnoreCase("gpg")) {

                    //if the file extension is GPG then we need to decrypt it
                    String decryptedFilename = FilenameUtils.removeExtension(encryptedFilename); //the decrypted file name is the filename minus ".gpg"
                    String decryptedTempFile = FilenameUtils.concat(tempDir, decryptedFilename);

                    String privateKey = dbConfiguration.getPgpConfiguration().getPgpRecipientPrivateKey();
                    String privateKeyPassword = dbConfiguration.getPgpConfiguration().getPgpRecipientPrivateKeyPassword();
                    String publicKey = dbConfiguration.getPgpConfiguration().getPgpSenderPublicKey();

                    LOG.info("   Decrypting file to: " + decryptedTempFile);
                    PgpUtil.decryptAndVerify(inputStream, decryptedTempFile, privateKey, privateKeyPassword, publicKey);

                    //also this is a good point to tag our GPG file so that our data retention policy thing works
                    if (encryptedSourceFile.startsWith("S3")
                            || encryptedSourceFile.startsWith("s3")) {
                        Map<String, String> tags = new HashMap<>();
                        tags.put("Emis", "raw");
                        FileHelper.setPermanentStorageTags(encryptedSourceFile, tags);
                    }

                } else {
                    //if not a CSV or GPG then something is wrong
                    throw new RuntimeException("Unexpected Emis file extension on " + encryptedFilename);
                }

            } finally {
                inputStream.close();
            }

        }
    }
}
