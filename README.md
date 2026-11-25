# vhp-sftpreader

The Endeavour **SFTP Reader** (downloads, decrypts, splits and validates supplier extracts: EMIS, TPP, Vision,
Barts, Homerton, Adastra, BHRUT, Imperial, NEMS; then notifies the Messaging API), ported to
**Java 21 / Tomcat 11 (Jakarta EE) / Casdoor**.

## What changed

| Area | Original | Now |
|---|---|---|
| Packaging | shaded fat jar, `java -jar sftpreader.jar <instance>`, one JVM per instance; BouncyCastle jars beside it | **WAR** (`vhp-sftpreader.war`); one deployment per instance; BouncyCastle in `WEB-INF/lib` |
| Start-up | `Main.main` | `web/SftpReaderContextListener` does the same as `Main.main` with no utility command, on a background thread. `Main` is kept for the command-line utilities |
| Web tier | embedded Jetty 9 + Jersey 2 declared, but never started (web.xml pointed at a missing `management` package) | Jersey 3.1 on Tomcat 11; `GET /api/status`; the static page shows it |
| Auth, inbound | none | `CasdoorAuthenticatorValve` on `/api/*` (roles `vhp_service` or `vhp_ui_admin`) |
| Auth, outbound (Messaging API `PostMessageAsync`) | `KeycloakClient` password grant, from the `keycloak_*` columns of the `configuration_eds` table | **Casdoor** token (`casdoor/CasdoorServiceAuth`) from the config DB (see below). Always sent: the `use_keycloak` switch is gone |
| `configuration_eds` table (SFTP Reader DB) | one row: `eds_url`, `use_keycloak`, `keycloak_*`, `temp_directory`, `shared_storage_path` | **Removed.** The settings are config DB rows (`api`, `storage`, `auth`, `datasteward`), read by `model/InstanceEdsConfig` |
| DPA check before posting | Messaging API `.../dsm/hasDPA/<ods>` with a Keycloak token (it read the DSM tables) | **vhp-datasteward** `GET /organisation/hasDPA` with the same Casdoor token (`datasteward/DataStewardClient`, copied from vhp-messaging-api) |
| `checkDpa` / `TestDPA` CLI commands | `OrganisationCache` (DSM DB) / Messaging API | `DpaCheck` → DataSteward |
| Scheduler | `while (true)` | stops when the webapp is undeployed (`SftpReaderTaskScheduler.stop()`); getters for the status page |
| JAXB API | `jakarta.xml.bind-api:2.3.2` | `javax.xml.bind:jaxb-api:2.3.1` (same `javax.xml.bind` API; the old coordinates clash with the 4.x jar Jersey 3 needs) |
| BouncyCastle | `*-jdk15on` 1.70 | `*-jdk18on` 1.86 (as on `vor-sync`) |
| SFTP Reader database | MySQL (`MySqlDataLayer`) or legacy PostgreSQL (`PostgresDataLayer`), picked from the JDBC connection | **MySQL only.** `PostgresDataLayer` / `PostgresConfigurationLock` and the Postgres DB scripts are removed. The reader's database comes from the `database` config row (`model/SftpReaderDb`), which refuses any JDBC url that isn't `jdbc:mysql:`. The `Main` utilities keep only their MySQL SQL. `MySqlDataLayer` throws `SftpReaderException` instead of the postgres library's `PgStoredProcException`, so the binary-only `common:postgres` library is no longer needed |

Unchanged: `app_id` = `sftpreader`, app sub-id = the instance name, the rest of the SFTP Reader database schema (MySQL 8,
`db/sftpreader.sql` and `db/sftpreader-database/src/main/mysql`), Slack, S3, logback-from-config-DB, the Graphite and
application heartbeats.

### Moving an existing `configuration_eds` row to the config DB

| `configuration_eds` column | Now |
|---|---|
| `eds_url` | `api` row, `api_url` |
| `temp_directory`, `shared_storage_path` | `storage` row, same names |
| `use_keycloak` | gone; the Casdoor service token is always sent |
| `keycloak_token_uri`, `keycloak_realm`, `keycloak_clientid` | `auth` row: `casdoor-endpoint`, `org-name`, `client-id` (+ `client-secret`, `app-name`) |
| `keycloak_username`, `keycloak_password` | `datasteward` row: `username`, `password` (the Casdoor service user) |

The table is no longer read, so it can be dropped once the values are copied: `DROP TABLE IF EXISTS configuration_eds;`

## Configuration (config DB, `app_id = sftpreader`)

The config DB connection comes from `CONFIG_JDBC_URL`, `CONFIG_JDBC_USERNAME` and `CONFIG_JDBC_PASSWORD`, as before.
The config DB is MySQL, so **`CONFIG_JDBC_CLASS=com.mysql.cj.jdbc.Driver` must be set**. ConfigManager otherwise defaults to
`org.postgresql.Driver`, and that driver is no longer in the WAR (it fails with `ClassNotFoundException: org.postgresql.Driver`).

| config_id | Used by | Notes |
|---|---|---|
| `auth` | Casdoor valve, and default client for the service token | Same format as the other VHP apps: `casdoor-endpoint`, `org-name`, `app-name`, `client-id`, `client-secret` (optional `tls`, `gateway`) |
| `datasteward` | **New**: where DataSteward is (DPA checks), and the identity used to call DataSteward and the Messaging API | see below. Same row and fields as vhp-messaging-api |
| `api` | **New**, required: the Messaging API address the reader posts batches to (was `eds_url`) | `{"api_url": "https://<host>/vhp-api/api/PostMessageAsync"}` |
| `storage` | **New**, required: where files are staged and stored (were `temp_directory` / `shared_storage_path`). The default; a configuration can override it (below) | `{"temp_directory": "/path/tmp", "shared_storage_path": "s3://<bucket>/path"}` |
| `database` | **New**, required: the SFTP Reader's own MySQL database (was the Endeavour `db_sftp_reader` row) | `{"url": "jdbc:mysql://<host>:3306/sftpreader", "username": "...", "password": "...", "pool": {"min-idle": 1, "max-pool-size": 3}}`. Same format as the other VHP apps; `pool` is optional |
| core DB rows, `logback`, Slack, AWS | unchanged | as for the original SFTP Reader. TPP re-bulk filtering still uses the Endeavour `db_sftp_reader_hashes` row |

Without the `api` or `storage` row the reader fails at start-up with an error naming the missing row, as it did
when the `configuration_eds` row was missing.

### Per-configuration storage (`configuration.temp_directory` / `shared_storage_path`)

The `storage` row is the default for every configuration. A configuration can override either value with the
nullable columns `temp_directory` and `shared_storage_path` of its row in the SFTP Reader DB's `configuration`
table (added 2026-11-25; `db/migrations/2026-11-25_configuration_storage_paths.sql`, skip it where the columns
already exist). NULL or blank means "use the `storage` row". The original reader had no such columns: its one
`configuration_eds` row applied to every configuration.

- Resolved once per run by `DbInstance.getEdsConfiguration(DbConfiguration)` (a copy, so configurations don't
  affect each other) and used for every step: download, unzip/decrypt, validate, split, bulk detection, notify,
  housekeeping, and the `Main` utilities.
- Files go to `<shared_storage_path>/<local_root_path>/<batch>/...`, as before, just under that root.
- **Notifications:** files under the default (`storage` row) root are still sent as paths relative to it, which the
  queue reader puts its own `shared_storage_path` in front of. A configuration with **its own** `shared_storage_path`
  sends **absolute** paths (`s3://other-bucket/...`, `/mnt/...`). Only the VHP queue reader (its `ExchangeHelper`
  override, vhp-queuereader README) reads these. It checks it can read the location and, if not, fails the exchange
  with an error naming the location and the access to set up, so give the queue reader access (S3 bucket policy /
  IAM, or the share mounted at the same path) before setting the column. A `temp_directory` override needs nothing
  on the queue reader side.
- Changing a configuration's `shared_storage_path` doesn't move files already stored. Files already notified
  under the old root were recorded with the old (relative) paths, so a late Barts/Imperial file for an old batch
  can be notified again after the change.
- If the columns are missing, the reader logs one warning and every configuration uses the `storage` row.
- dds-ui's SFTP Reader page doesn't show or edit these columns yet; set them in SQL.

The `datasteward` row:

```json
{
  "url": "https://<host>/vhp-datasteward/machine-api",
  "username": "svc-sftpreader",
  "password": "...",
  "cache-seconds": 300,
  "timeout-seconds": 30,
  "casdoor-endpoint": "...", "client-id": "...", "client-secret": "..."
}
```

- Only `url` is required. Casdoor endpoint and client fields default to the `auth` row.
- There is no separate `casdoor-service` row any more (it was never deployed). The one service user in this row is
  used for both DataSteward and the Messaging API.
- With `username`/`password` the token comes from the **password grant** as that Casdoor user. That user needs
  `vhp_service` + `vhp_messaging_post` (Messaging API) and `vhp_service` (DataSteward).
- Without a username it falls back to **client_credentials**. Casdoor application tokens have no roles, so
  vhp-api and DataSteward currently refuse them (`docs/casdoor-app-tokens-and-roles.md`). **Use a service user until that is resolved.**
- If this row or the `auth` row is missing or wrong the reader still starts and keeps downloading and splitting. Notifying
  the Messaging API then fails with a clear error in the log and in the batch notification rows. That is the
  same place Keycloak or DPA failures showed up before.

### Instance name (one deployment per instance)

The listener runs the instance named by, in order:

1. the `instance_name` context-param. Set it per deployment, e.g. `conf/Catalina/localhost/vhp-sftpreader.xml`:
   ```xml
   <Context>
       <Parameter name="instance_name" value="EMIS01" override="false"/>
   </Context>
   ```
   To run several instances on one Tomcat, deploy the WAR under several context names
   (`vhp-sftpreader#EMIS01.war`, …), each with its own context file.
2. the `INSTANCE_NAME` system property (as the original jar accepted)
3. the `INSTANCE_NAME` environment variable

With none of these set the webapp still deploys. The status page then reports `NOT_CONFIGURED` and nothing is read.

`shutdown_wait_seconds` (default 60) is how long undeploy waits for a running task to finish before interrupting it.
Interrupting it mid-download is no worse than the old process being killed, but long downloads should be allowed to finish.

## Roles (Casdoor)

| What | Role |
|---|---|
| `GET /api/status` (valve `required_roles`) | `vhp_service` or `vhp_ui_admin` |
| Messaging API `PostMessageAsync` (called by the reader) | `vhp_service` + `vhp_messaging_post` on the service user |
| DataSteward `hasDPA` (called by the reader) | `vhp_service` on the service user |

The static page (`/vhp-sftpreader/`) is open. It gets a Casdoor token (password grant) or takes a pasted
one, then shows `/api/status`. The status response contains only configuration IDs, states and times, never
SFTP hosts, keys or passwords.

## Build and deploy

```bash
mvn clean package          # JDK 21; produces target/vhp-sftpreader.war and target/sftpreader-1.0-SNAPSHOT.jar
```

The build makes two deployables from the same code. **Run one or the other for an instance, never both.**

### As a stand-alone jar (how the original was deployed)

`target/` holds `sftpreader-1.0-SNAPSHOT.jar` (next to the WAR) and five BouncyCastle jars (`bc*.jar`). Deploy the
jar together with those five, in one folder: the BouncyCastle jars
are signed, so they stay outside the main jar and are found through its manifest `Class-Path`, as in the original.

```bash
java -jar sftpreader-1.0-SNAPSHOT.jar <instance>
```

- Java 21. Set `CONFIG_JDBC_URL`, `CONFIG_JDBC_USERNAME`, `CONFIG_JDBC_PASSWORD` and `CONFIG_JDBC_CLASS=com.mysql.cj.jdbc.Driver`
  in the service's environment.
- This runs `Main`, like the old service: no Tomcat, no Casdoor valve, no `/api/status` and no status page. The
  only Casdoor use is the outbound service token. A fatal start-up error exits with a non-zero code.
- The command-line utilities work the same way: `java -jar sftpreader-1.0-SNAPSHOT.jar <instance> <command> ...`
- The jar is built by `maven-assembly-plugin` (`src/assembly/runnable-jar.xml`), not shade, because the project's
  main artifact is the WAR.

### As a WAR on Tomcat

- Tomcat 11 on Java 21. `casdoor-tomcat-valve` and its dependencies go in `$CATALINA_BASE/lib`, the same as for the other VHP apps.
- The command-line utilities (`DecryptGpg`, `CheckForBulks`, `checkDpa`, `loadTppSRCode`, …) still live in `Main`.
  Run them against the exploded WAR:
  ```bash
  java -cp "WEB-INF/classes:WEB-INF/lib/*" org.endeavourhealth.sftpreader.Main <instance> <command> ...
  ```
  With just `<instance>`, `Main` still runs the reader outside Tomcat, as the old jar did.

## Known limitations / follow-ups

- **Service-to-service auth** relies on a Casdoor service user (password grant) until the app-token roles issue
  is decided (`docs/casdoor-app-tokens-and-roles.md`). The reader is a second caller affected by it, after vhp-api.
- **Deploy one instance per webapp.** `ConfigManager` and `Configuration` are static singletons, so two instances in one webapp
  can't work. Separate context names give separate class loaders, so that's fine.
- `PgpUtil` registers BouncyCastle as a JVM-wide security provider. The listener removes it on undeploy so a
  redeploy doesn't leak the old class loader. Several reader webapps in one Tomcat each register their own copy while running.
- The WAR has only the MySQL JDBC driver (`mysql-connector-java` 8.0.19 from the Endeavour BOM). The PostgreSQL driver is
  excluded from common core, because both the SFTP Reader DB and the config DB are MySQL and no bundled class links to it.
- If the config DB's `logback` row uses the old `org.endeavourhealth.common.postgres.logdigest` appender, it must be
  changed, because that library is no longer in the WAR.
- Hibernate 5.2 (from common core) runs with javassist 3.30, as in vhp-messaging-api. The reader barely uses it (core DAL only).


## Tests

`mvn test` (55 tests):

- `CasdoorSendersTest`: `EdsSender` sends the Casdoor bearer token (password grant), reuses it, drops it after a 401, and fails clearly when not configured. `DpaCheck` goes to DataSteward
  with the same token and normalises parent/child codes. All of it runs against local HTTP stand-ins.
- `DataStewardClientTest` (from vhp-messaging-api), `DataStewardConfigTest`, `ServiceAuthConfigTest`, `InstanceEdsConfigTest`, `SftpReaderDbTest`, `SftpReaderContextListenerTest`.
- `DbInstanceEdsPerConfigurationTest`: per-configuration `temp_directory` / `shared_storage_path` override the `storage` row for that configuration only.
  `NotificationPayloadPathTest`: the real notification creator over real files sends relative paths for the default root and absolute ones for a configuration's own root.
  The `configuration` column check in `MySqlDataLayer` needs a real MySQL and is **not** tested.
- `Tomcat11SmokeTest`: deploys the real `web.xml`/`context.xml` on embedded Tomcat 11 with a stand-in valve that
  applies web.xml's `protected_paths`/`required_roles`. It checks the 401/403/200 on `/api/status`, the open static page,
  that deployment doesn't wait for the reader, and that with no config DB the reader reports `FAILED` and undeploys cleanly.
