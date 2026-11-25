-- MySQL dump 10.13  Distrib 8.0.44, for Win64 (x86_64)
--
-- Host: 10.0.200.64    Database: sftp_reader
-- ------------------------------------------------------
-- Server version	8.4.6

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `adastra_organisation_map`
--

DROP TABLE IF EXISTS adastra_organisation_map;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE adastra_organisation_map (
  ods_code varchar(255) NOT NULL,
  file_name_org_code varchar(255) NOT NULL,
  configuration_id varchar(100) NOT NULL,
  PRIMARY KEY (ods_code),
  KEY ix_adastra_organisation_map_configuration (configuration_id,file_name_org_code),
  CONSTRAINT adastra_organisation_map_fk FOREIGN KEY (configuration_id) REFERENCES configuration (configuration_id)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `batch`
--

DROP TABLE IF EXISTS batch;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE batch (
  batch_id int NOT NULL AUTO_INCREMENT,
  configuration_id varchar(100) NOT NULL,
  interface_type_id int NOT NULL,
  batch_identifier varchar(500) NOT NULL,
  local_relative_path varchar(1000) NOT NULL,
  insert_date datetime DEFAULT NULL,
  sequence_number int DEFAULT NULL,
  is_complete tinyint(1) NOT NULL,
  complete_date datetime DEFAULT NULL,
  extract_date datetime DEFAULT NULL,
  extract_cutoff datetime DEFAULT NULL,
  PRIMARY KEY (batch_id),
  UNIQUE KEY batch_configurationid_batchid_uq (configuration_id,batch_id),
  UNIQUE KEY batch_configurationid_sequencenumber_uq (configuration_id,sequence_number),
  KEY batch_configurationid_interfacetypeid_fk (configuration_id,interface_type_id),
  KEY ix_batch_configuration_identifer (configuration_id,batch_identifier),
  CONSTRAINT batch_configurationid_interfacetypeid_fk FOREIGN KEY (configuration_id, interface_type_id) REFERENCES configuration (configuration_id, interface_type_id)
) ENGINE=InnoDB AUTO_INCREMENT=1019 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `batch_file`
--

DROP TABLE IF EXISTS batch_file;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE batch_file (
  batch_file_id int NOT NULL AUTO_INCREMENT,
  batch_id int NOT NULL,
  interface_type_id int NOT NULL,
  file_type_identifier varchar(1000) NOT NULL,
  insert_date datetime DEFAULT NULL,
  filename varchar(1000) NOT NULL,
  remote_created_date datetime DEFAULT NULL,
  remote_size_bytes bigint NOT NULL,
  is_downloaded tinyint(1) NOT NULL DEFAULT '0',
  download_date datetime DEFAULT NULL,
  is_deleted tinyint(1) DEFAULT '0',
  PRIMARY KEY (batch_file_id),
  UNIQUE KEY batchfile_batchid_filename_uq (batch_id,filename),
  CONSTRAINT batchfile_batchid_fk FOREIGN KEY (batch_id) REFERENCES batch (batch_id)
) ENGINE=InnoDB AUTO_INCREMENT=14647 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `batch_split`
--

DROP TABLE IF EXISTS batch_split;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE batch_split (
  batch_split_id int NOT NULL AUTO_INCREMENT,
  batch_id int NOT NULL,
  configuration_id varchar(100) NOT NULL,
  local_relative_path varchar(1000) NOT NULL,
  organisation_id varchar(100) NOT NULL,
  have_notified tinyint(1) NOT NULL DEFAULT '0',
  notification_date datetime DEFAULT NULL,
  is_bulk tinyint(1) DEFAULT NULL COMMENT 'whether this extract is known to contain a bulk extract',
  has_patient_data tinyint(1) DEFAULT NULL COMMENT 'whether this extract contains clinical or patient data',
  PRIMARY KEY (batch_split_id),
  UNIQUE KEY log_batchsplit_configurationid_batchid_batchsplitid_uq (configuration_id,batch_id,batch_split_id),
  KEY log_batchsplit_configurationid_localrelativepath (configuration_id,local_relative_path),
  KEY batch_split_batch_id (batch_id),
  CONSTRAINT batchsplit_configurationid_batchid_fk FOREIGN KEY (configuration_id, batch_id) REFERENCES batch (configuration_id, batch_id)
) ENGINE=InnoDB AUTO_INCREMENT=1011 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `configuration`
--

DROP TABLE IF EXISTS configuration;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE configuration (
  configuration_id varchar(255) NOT NULL,
  interface_type_id int NOT NULL,
  poll_frequency_seconds int NOT NULL,
  local_root_path varchar(1000) NOT NULL,
  configuration_friendly_name varchar(100) NOT NULL,
  software_content_type varchar(100) NOT NULL,
  software_version varchar(100) NOT NULL,
  temp_directory varchar(255) NULL, -- VHP: optional, overrides the storage config row
  shared_storage_path varchar(255) NULL, -- VHP: optional, overrides the storage config row
  PRIMARY KEY (configuration_id),
  UNIQUE KEY configurationfriendlyname_uq (configuration_friendly_name),
  UNIQUE KEY configurationid_interfacetypeid_uq (configuration_id,interface_type_id),
  KEY configuration_interfacetypeid_fk (interface_type_id),
  CONSTRAINT configuration_interfacetypeid_fk FOREIGN KEY (interface_type_id) REFERENCES interface_type (interface_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- VHP port: the `configuration_eds` table is no longer used. Its settings are in the config database
-- (app_id sftpreader, rows `api`, `storage`, `auth`, `datasteward`) - see the README.
-- On an existing database: DROP TABLE IF EXISTS configuration_eds;  (once the values are copied across)
--

--
-- Table structure for table `configuration_kvp`
--

DROP TABLE IF EXISTS configuration_kvp;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE configuration_kvp (
  configuration_id varchar(100) NOT NULL,
  `key` varchar(100) NOT NULL,
  `value` varchar(1000) NOT NULL,
  PRIMARY KEY (configuration_id,`key`),
  CONSTRAINT configurationkvp_configurationid_fk FOREIGN KEY (configuration_id) REFERENCES configuration (configuration_id)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `configuration_paused_notifying`
--

DROP TABLE IF EXISTS configuration_paused_notifying;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE configuration_paused_notifying (
  configuration_id varchar(255) NOT NULL,
  dt_paused datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (configuration_id)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `configuration_pgp`
--

DROP TABLE IF EXISTS configuration_pgp;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE configuration_pgp (
  configuration_id varchar(100) NOT NULL,
  file_extension_filter varchar(100) NOT NULL,
  sender_public_key mediumtext NOT NULL,
  recipient_public_key mediumtext NOT NULL,
  recipient_private_key mediumtext NOT NULL,
  recipient_private_key_password varchar(1000) NOT NULL,
  PRIMARY KEY (configuration_id),
  CONSTRAINT configurationpgp_configurationid_fk FOREIGN KEY (configuration_id) REFERENCES configuration (configuration_id)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `configuration_polling_attempt`
--

DROP TABLE IF EXISTS configuration_polling_attempt;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE configuration_polling_attempt (
  configuration_id varchar(100) NOT NULL,
  attempt_started datetime NOT NULL,
  attempt_finished datetime NOT NULL,
  exception_text mediumtext,
  files_downloaded int DEFAULT NULL,
  batches_completed int DEFAULT NULL,
  batch_splits_notified_ok int DEFAULT NULL,
  batch_splits_notified_failure int DEFAULT NULL,
  PRIMARY KEY (configuration_id,attempt_started),
  CONSTRAINT configuration_polling_attempt_fk FOREIGN KEY (configuration_id) REFERENCES configuration (configuration_id)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `configuration_sftp`
--

DROP TABLE IF EXISTS configuration_sftp;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE configuration_sftp (
  configuration_id varchar(100) NOT NULL,
  hostname varchar(100) NOT NULL,
  `port` int NOT NULL,
  remote_path varchar(1000) NOT NULL,
  username varchar(100) NOT NULL,
  client_public_key mediumtext NOT NULL,
  client_private_key mediumtext NOT NULL,
  client_private_key_password varchar(1000) NOT NULL,
  host_public_key mediumtext NOT NULL,
  PRIMARY KEY (configuration_id),
  CONSTRAINT configurationsftp_configurationid_fk FOREIGN KEY (configuration_id) REFERENCES configuration (configuration_id)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `emis_organisation_map`
--

DROP TABLE IF EXISTS emis_organisation_map;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE emis_organisation_map (
  guid varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  ods_code varchar(255) NOT NULL,
  PRIMARY KEY (guid),
  KEY emisorganisationmap_guid_ix (guid)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `instance`
--

DROP TABLE IF EXISTS instance;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `instance` (
  instance_name varchar(100) NOT NULL,
  hostname varchar(500) DEFAULT NULL,
  http_management_port int DEFAULT NULL,
  last_config_get_date datetime DEFAULT NULL,
  PRIMARY KEY (instance_name)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `instance_configuration`
--

DROP TABLE IF EXISTS instance_configuration;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE instance_configuration (
  instance_name varchar(100) NOT NULL,
  configuration_id varchar(100) NOT NULL,
  PRIMARY KEY (instance_name,configuration_id),
  UNIQUE KEY instanceconfiguration_configurationid_uq (configuration_id),
  CONSTRAINT instanceconfiguration_configurationid_fk FOREIGN KEY (configuration_id) REFERENCES configuration (configuration_id),
  CONSTRAINT instanceconfiguration_instancename_fk FOREIGN KEY (instance_name) REFERENCES `instance` (instance_name)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `interface_file_type`
--

DROP TABLE IF EXISTS interface_file_type;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE interface_file_type (
  interface_type_id int NOT NULL,
  file_type_identifier varchar(1000) NOT NULL,
  PRIMARY KEY (interface_type_id,file_type_identifier),
  CONSTRAINT interfacefiletype_interfacetypeid_fk FOREIGN KEY (interface_type_id) REFERENCES interface_type (interface_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `interface_type`
--

DROP TABLE IF EXISTS interface_type;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE interface_type (
  interface_type_id int NOT NULL,
  interface_type_name varchar(1000) NOT NULL,
  data_frequency_days int NOT NULL COMMENT 'how often we expect data in days',
  PRIMARY KEY (interface_type_id),
  UNIQUE KEY interfacetype_interfacetypename_uq (interface_type_name)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notification_message`
--

DROP TABLE IF EXISTS notification_message;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE notification_message (
  notification_message_id int NOT NULL AUTO_INCREMENT,
  batch_id int NOT NULL,
  batch_split_id int NOT NULL,
  configuration_id varchar(100) NOT NULL,
  message_uuid char(36) NOT NULL,
  `timestamp` datetime DEFAULT NULL,
  outbound mediumtext,
  inbound mediumtext,
  was_success tinyint(1) NOT NULL,
  error_text mediumtext,
  PRIMARY KEY (notification_message_id),
  UNIQUE KEY notificationmessage_messageuuid_uq (message_uuid),
  KEY notificationmessage_configurationid_batchid_batchsplitid_fk (configuration_id,batch_id,batch_split_id),
  KEY ix_dds_ui_helper (batch_id,batch_split_id,`timestamp`),
  CONSTRAINT notificationmessage_configurationid_batchid_batchsplitid_fk FOREIGN KEY (configuration_id, batch_id, batch_split_id) REFERENCES batch_split (configuration_id, batch_id, batch_split_id)
) ENGINE=InnoDB AUTO_INCREMENT=10824 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tpp_organisation_gms_registration_map`
--

DROP TABLE IF EXISTS tpp_organisation_gms_registration_map;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE tpp_organisation_gms_registration_map (
  organisation_id varchar(20) NOT NULL,
  patient_id bigint NOT NULL,
  gms_organisation_id varchar(20) NOT NULL,
  PRIMARY KEY (organisation_id,patient_id,gms_organisation_id),
  KEY tpp_organisation_gms_registration_map_organisation_id_ix (organisation_id)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tpp_organisation_map`
--

DROP TABLE IF EXISTS tpp_organisation_map;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE tpp_organisation_map (
  ods_code varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (ods_code)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `unknown_file`
--

DROP TABLE IF EXISTS unknown_file;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE unknown_file (
  unknown_file_id int NOT NULL AUTO_INCREMENT,
  configuration_id varchar(100) NOT NULL,
  insert_date datetime DEFAULT NULL,
  filename varchar(1000) NOT NULL,
  remote_created_date datetime DEFAULT NULL,
  remote_size_bytes bigint NOT NULL,
  PRIMARY KEY (unknown_file_id),
  KEY unknownfile_configurationid_fk (configuration_id),
  CONSTRAINT unknownfile_configurationid_fk FOREIGN KEY (configuration_id) REFERENCES configuration (configuration_id)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed
