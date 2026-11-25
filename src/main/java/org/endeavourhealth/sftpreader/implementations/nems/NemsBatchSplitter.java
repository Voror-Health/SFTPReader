package org.endeavourhealth.sftpreader.implementations.nems;

import com.mysql.cj.log.Log;
import org.apache.commons.io.FilenameUtils;
import org.endeavourhealth.common.utility.FileHelper;
import org.endeavourhealth.sftpreader.implementations.SftpBatchSplitter;
import org.endeavourhealth.sftpreader.implementations.adastra.utility.AdastraConstants;
import org.endeavourhealth.sftpreader.implementations.nems.utility.NemsConstants;
import org.endeavourhealth.sftpreader.model.DataLayerI;
import org.endeavourhealth.sftpreader.model.db.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class NemsBatchSplitter extends SftpBatchSplitter {
    private static final Logger LOG = LoggerFactory.getLogger(org.endeavourhealth.sftpreader.implementations.nems.NemsBatchSplitter.class);

    @Override
    public List<BatchSplit> splitBatch(Batch batch, Batch lastCompleteBatch, DataLayerI db, DbInstanceEds instanceConfiguration, DbConfiguration dbConfiguration) throws Exception {

        String sharedStorageDir = instanceConfiguration.getSharedStoragePath();
        String tempDir = instanceConfiguration.getTempDirectory();
        String configurationDir = dbConfiguration.getLocalRootPath();
        String batchDir = batch.getLocalRelativePath();

        String sourcePermDir = FilenameUtils.concat(sharedStorageDir, configurationDir);
        sourcePermDir = FilenameUtils.concat(sourcePermDir, batchDir);

        String splitTempDir = FilenameUtils.concat(tempDir, configurationDir);
        splitTempDir = FilenameUtils.concat(splitTempDir, batchDir);

        return copyToPermanentStorageAndCreateBatchSplits(batch.getBatchFiles(), batch, sourcePermDir, splitTempDir);
    }

    private List<BatchSplit> copyToPermanentStorageAndCreateBatchSplits(List<BatchFile> batchFiles, Batch batch,
                                                                        String sourcePermDir, String splitTempDir) throws Exception {
        List<BatchSplit> ret = new ArrayList<>();

        //create a batch split for each ODS code
        for (BatchFile batchFile: batchFiles) {

            String localPath = batch.getLocalRelativePath();
            String ext = "";
            if(batchFile.getFileTypeIdentifier().equals(NemsConstants.FILE_ID_BIRTH_NOTIFICATION)) {
                ext = NemsConstants.FILEPATH_ID_BIRTH_NOTIFICATION;
                localPath = FilenameUtils.concat(localPath, ext);
            } else if(batchFile.getFileTypeIdentifier().equals(NemsConstants.FILE_ID_DEATH_NOTIFICATION)) {
                ext = NemsConstants.FILEPATH_ID_DEATH_NOTIFICATION;
                localPath = FilenameUtils.concat(localPath, ext);
            }

            BatchSplit batchSplit = new BatchSplit();
            batchSplit.setBatchId(batch.getBatchId());
            batchSplit.setLocalRelativePath(localPath);
            batchSplit.setOrganisationId("X26");
            ret.add(batchSplit);

            //copy everything to temp storage
            String filePermPath = FilenameUtils.concat(sourcePermDir, batchFile.getFilename());
            String fileTempPath = FilenameUtils.concat(splitTempDir, batchFile.getFilename());
            File dstDir = new File(splitTempDir);

            //if the temp folder does exist, we want to make sure that all previous content is deleted
            FileHelper.deleteRecursiveIfExists(dstDir);
            FileHelper.createDirectoryIfNotExists(dstDir);

            //copy the case file from S3 to the local temp dir
            LOG.debug("Copying from " + sourcePermDir + " to " + splitTempDir);
            InputStream is = FileHelper.readFileFromSharedStorage(filePermPath);
            Files.copy(is, new File(fileTempPath).toPath(), StandardCopyOption.REPLACE_EXISTING);
            is.close();

            //copy everything to storage
            String storagePath = FilenameUtils.concat(sourcePermDir, ext);

            String nemsDir = splitTempDir;
            File[] splitFiles = new File(nemsDir).listFiles();
            LOG.trace("STORAGE PATH: " + storagePath);
            LOG.trace("TEMP DIR: " + nemsDir);
            LOG.trace("Total files in temp directory: " + splitFiles.length);
            LOG.trace("type: " +  ext);
            LOG.trace("Copying " + splitFiles.length + " files from " + nemsDir + " to permanent storage for " + ext);

            for (File splitFile: splitFiles) {
                if(splitFile.isDirectory()){
                    continue;
                }
                LOG.trace("file name: " + splitFile.getName());
                String fileName = splitFile.getName();
                String storageFilePath = FilenameUtils.concat(storagePath, fileName);

                FileHelper.writeFileToSharedStorage(storageFilePath, splitFile);
                FileHelper.deleteFromSharedStorage(FilenameUtils.concat(sourcePermDir, fileName));
            }
        }

        return ret;
    }
}
