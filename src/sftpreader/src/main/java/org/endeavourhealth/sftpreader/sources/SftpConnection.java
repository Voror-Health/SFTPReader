package org.endeavourhealth.sftpreader.sources;

import com.google.common.base.Strings;
import com.jcraft.jsch.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.endeavourhealth.sftpreader.utilities.RemoteFile;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public class SftpConnection extends Connection {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(SftpConnection.class);

    //private ConnectionDetails connectionDetails;
    private JSch jSch;
    private Session session;
    private ChannelSftp channel;

    public SftpConnection(ConnectionDetails connectionDetails) {
        super(connectionDetails);
        Validate.notEmpty(connectionDetails.getHostname(), "hostname is empty");
        Validate.notEmpty(connectionDetails.getUsername(), "username is empty");
        Validate.isTrue(connectionDetails.getPort() > 0, "port must be positive");
    }

    public void open() throws JSchException, IOException, SftpConnectionException {

        //JSch.setLogger(new Logger());   //uncomment to enable JSch verbose SSH logging

        this.jSch = new JSch();

        String prvKey = getConnectionDetails().getClientPrivateKey().trim();
        String pw = getConnectionDetails().getClientPrivateKeyPassword().trim();
        if (!Strings.isNullOrEmpty(prvKey)) {
            jSch.addIdentity("client-private-key", prvKey.getBytes(), null, pw.getBytes());
        }

        //NOTE: To find the public host key, use SSH sftp to connect to the server and then copy the
        //record from the ~/.ssh/known_hosts file. It's easier to work out the correct record if the known_hosts
        //is first backed up, then emptied, then you know exactly which record is for the new server
        String hostPublicKey = getConnectionDetails().getHostPublicKey();
        if (StringUtils.isNotBlank(hostPublicKey)) {
            String knownHosts = getConnectionDetails().getKnownHostsString();
            jSch.setKnownHosts(new ByteArrayInputStream(knownHosts.getBytes()));
            this.session = jSch.getSession(getConnectionDetails().getUsername(), getConnectionDetails().getHostname(), getConnectionDetails().getPort());
        } else {
            this.session = jSch.getSession(getConnectionDetails().getUsername(), getConnectionDetails().getHostname(), getConnectionDetails().getPort());
            this.session.setConfig("StrictHostKeyChecking", "no");
        }

        // no private key supplied and using standard password authentication
        if (Strings.isNullOrEmpty(prvKey) && !Strings.isNullOrEmpty(pw)) {
            session.setPassword(pw);
        }

        //Emis connection has been stalling on weekends when trying to list directories
        //so set the session timeout to ten mins (in millis) to try to avoid this. Since it has consistently
        //happened for three successive weeks only on weeks, I believe that this is some kind of consequent
        //of Emis restarting servers or something similar
        session.setTimeout(10 * 60 * 1000);

        LOG.trace("Session set up");

        this.session.connect();
        LOG.trace("Session connected");

        this.channel = (ChannelSftp)session.openChannel("sftp");
        this.channel.connect();
        LOG.trace("Channel connected");

        //adding this to try to get past an error with new Emis server
        /*KnownHosts knownHosts = (KnownHosts)jSch.getHostKeyRepository();
        for (HostKey key: knownHosts.getHostKey()) {
            LOG.info("Public key: " + key.getKey());
            LOG.info("Public fingerprint: " + key.getFingerPrint(jSch));
        }

        this.session.setUserInfo(new TestUserInfo());

        try {
            this.session.connect();

            this.channel = (ChannelSftp)session.openChannel("sftp");
            this.channel.connect();
        } catch (Exception ex) {

            HostKey serverHostKey = session.getHostKey();
            LOG.info("Server Public key: " + serverHostKey.getKey());
            LOG.info("Server Public fingerprint: " + serverHostKey.getFingerPrint(jSch));

            throw ex;
        }*/
    }

    public static class Logger implements com.jcraft.jsch.Logger {
        static java.util.Hashtable name=new java.util.Hashtable();
        static{
            name.put(new Integer(DEBUG), "DEBUG: ");
            name.put(new Integer(INFO), "INFO: ");
            name.put(new Integer(WARN), "WARN: ");
            name.put(new Integer(ERROR), "ERROR: ");
            name.put(new Integer(FATAL), "FATAL: ");
        }
        public boolean isEnabled(int level){
            return true;
        }
        public void log(int level, String message){
            LOG.info(name.get(new Integer(level)).toString());
            LOG.info(message);
        }
    }


    @SuppressWarnings("unchecked")

    //THIS METHOD CHANGE (including new version of getFile) WAS FOR THE EMIS AWS MIGRATION.  WORKS FOR THAT BUT NOT FOR CURRENT FEED
    public List<RemoteFile> getFileList(String remotePath) throws SftpException {

        //an error is raised if we try to list the files without first changing into the directory
        channel.cd(remotePath);

        //trying alternatives that work on all known SFTP servers
        Vector<ChannelSftp.LsEntry> fileList = channel.ls(".");
        //Vector<ChannelSftp.LsEntry> fileList = channel.ls("\\");

        //temporary debug for listing files out
//        List<String> sftpListing = new ArrayList<>();
//        for (ChannelSftp.LsEntry entry: fileList) {
//            sftpListing.add(entry.getFilename() + "("+entry.getAttrs().getSize()+" bytes)" + " isDir? -> "+entry.getAttrs().isDir());
//        }
//        Arrays.sort(sftpListing.toArray());
//        LOG.debug(remotePath+" --> file listing: ");
//        LOG.debug(String.join("\r\n", sftpListing));
        ///////////////////////////////////

        List<RemoteFile> ret = new ArrayList<>();

        for (ChannelSftp.LsEntry entry: fileList) {
            if (entry.getAttrs().isDir()) {
                //for any directory found, recurse and get a listing of the subdirectory
                String dirName = entry.getFilename();
                //ignore . and .. as a directory to prevent endless loop when starting in a sub directory
                if (dirName.equalsIgnoreCase(".") || dirName.equalsIgnoreCase("..")) {
                    continue;
                }
                //LOG.debug("Directory found: "+dirName);

                List<RemoteFile> childList = getFileList(dirName);

                //need to prefix the object file name with the path
                for (RemoteFile childEntry: childList) {
                    //have to manually use the Linux file separator so this code will work on Windows
                    String path = dirName + "/" + childEntry.getFullPath();

                    //LOG.debug("Directory file entry found: "+path);

                    //String path = FilenameUtils.concat(dirName, childEntry.getFullPath());
                    RemoteFile f = new RemoteFile(path, childEntry.getFileSizeBytes(), childEntry.getLastModified());

                    //LOG.debug("Directory file entry added");

                    ret.add(f);
                }

                //step back out of the sub-directory
                channel.cd("..");

            } else {

                //LOG.debug("Non directory file entry found: "+entry.getFilename());

                RemoteFile f = new RemoteFile(entry.getFilename(),
                        entry.getAttrs().getSize(),
                        LocalDateTime.ofInstant(new Date((long)entry.getAttrs().getMTime() * 1000L).toInstant(), ZoneId.systemDefault()));
                ret.add(f);

                //LOG.debug("Non directory file entry added");
            }
        }

        return ret;
    }

    //AWS and current feed version, gets explicit remote file regardless of position in folder
    public InputStream getFile(String remotePath) throws SftpException {
        return channel.get(remotePath);
    }

    //Current feed version - single fileList stream, no directories handled
//    public List<RemoteFile> getFileList(String remotePath) throws SftpException {
//
//        //an error is raised if we try to list the files without first changing into the directory
//        channel.cd(remotePath);
//
//        //trying alternatives that work on all known SFTP servers
//        Vector<ChannelSftp.LsEntry> fileList = channel.ls(".");
//        //Vector<ChannelSftp.LsEntry> fileList = channel.ls("\\");
//
//        return fileList
//                .stream()
//                .filter(t -> !t.getAttrs().isDir())
//                .map(t ->
//                        new RemoteFile(t.getFilename(),
//                                t.getAttrs().getSize(),
//                                LocalDateTime.ofInstant(new Date((long)t.getAttrs().getMTime() * 1000L).toInstant(), ZoneId.systemDefault())
//                        )
//                )
//                .collect(Collectors.toList());
//    }

        //current feed version, filename only  **********
//    public InputStream getFile(String remotePath) throws SftpException {
//        String name = FilenameUtils.getName(remotePath);
//        return channel.get(name);
//    }

    /* deleteFile, cd, mkdir methods now re-commented out,
     * put method remains uncommented for use in DataGenerator
     */

    /* public void deleteFile(String remotePath) throws SftpException {
        channel.rm(remotePath);
    } */

    /* public void cd(String remotePath) throws SftpException {
        channel.cd(remotePath);
    } */

    public void put(String localPath, String destinationPath) throws SftpException {
        channel.put(localPath, destinationPath);
    }

    /* public void mkDir(String path) throws SftpException {
        channel.mkdir(path);
    } */

    public void close() {
        if (channel != null && channel.isConnected())
            channel.disconnect();

        if (session != null && session.isConnected())
            session.disconnect();
    }

    /*class TestUserInfo implements UserInfo {

        @Override
        public String getPassphrase() {
            LOG.info("UserInfo getPassphrase");
            return null;
        }

        @Override
        public String getPassword() {
            LOG.info("UserInfo getPassword");
            return "";
        }

        @Override
        public boolean promptPassword(String message) {
            LOG.info("UserInfo promptPassword: " + message);
            return true;
        }

        @Override
        public boolean promptPassphrase(String message) {
            LOG.info("UserInfo promptPassphrase: " + message);
            return false;
        }

        @Override
        public boolean promptYesNo(String message) {
            LOG.info("UserInfo promptYesNo: " + message);
            return true;
        }

        @Override
        public void showMessage(String message) {
            LOG.info("UserInfo showMessage: " + message);
        }
    }*/
}
