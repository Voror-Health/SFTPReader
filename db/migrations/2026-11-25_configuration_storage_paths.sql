-- VHP SFTP Reader: optional per-configuration storage locations.
--
-- When set, these override the instance-wide 'storage' config row (config DB, app_id sftpreader) for that
-- configuration only. NULL (the default) keeps the 'storage' row's value.
--
--   temp_directory       where the configuration's batches are unzipped/decrypted/split
--   shared_storage_path  the root its files are stored under (<root>/<local_root_path>/<batch>/...)
--
-- A configuration with its own shared_storage_path is notified to the Messaging API with ABSOLUTE file paths,
-- and only the VHP queue reader (vhp-queuereader, ExchangeHelper override) can read those. The queue reader
-- must be able to read the location too (S3 bucket policy / IAM role, or the file share mounted at the same
-- path); if it can't, the exchange fails with an error naming the location.
--
-- Some databases already have these columns (added by hand). MySQL 8 has no ADD COLUMN IF NOT EXISTS, so
-- check first and skip the ALTER if they are there:
--   SELECT column_name FROM information_schema.columns
--   WHERE table_schema = DATABASE() AND table_name = 'configuration'
--     AND column_name IN ('temp_directory', 'shared_storage_path');
-- The reader works with or without them (it checks at start-up and logs a warning if they are missing).

ALTER TABLE configuration
  ADD COLUMN temp_directory varchar(255) NULL,
  ADD COLUMN shared_storage_path varchar(255) NULL;
