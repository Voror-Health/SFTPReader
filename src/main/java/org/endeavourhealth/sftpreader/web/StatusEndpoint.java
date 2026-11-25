package org.endeavourhealth.sftpreader.web;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.endeavourhealth.sftpreader.Configuration;
import org.endeavourhealth.sftpreader.SftpReaderTaskInfo;
import org.endeavourhealth.sftpreader.SftpReaderTaskScheduler;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only status of the SFTP Reader running in this webapp ({@code GET /api/status}). Only identifiers and
 * timings are returned, never configuration details (SFTP hosts, keys, passwords).
 */
@Path("/status")
public class StatusEndpoint {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> status() {
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("state", SftpReaderRuntime.getState().name());
        ret.put("instanceName", SftpReaderRuntime.getInstanceName());
        ret.put("stateChanged", SftpReaderRuntime.getStateChanged().toString());
        ret.put("error", SftpReaderRuntime.getError());

        Configuration configuration = SftpReaderRuntime.getConfiguration();
        if (configuration != null) {
            ret.put("machineName", configuration.getMachineName());
        }

        SftpReaderTaskScheduler scheduler = SftpReaderRuntime.getScheduler();
        List<Map<String, Object>> tasks = new ArrayList<>();
        if (scheduler != null) {
            SftpReaderTaskInfo running = scheduler.getCurrentRunningTask();
            Date runningSince = scheduler.getCurrentTaskStarted();
            List<SftpReaderTaskInfo> taskInfos = scheduler.getTasks();
            if (taskInfos != null) {
                for (SftpReaderTaskInfo task : taskInfos) {
                    Map<String, Object> t = new LinkedHashMap<>();
                    t.put("configurationId", task.getTaskName());
                    t.put("nextScheduled", task.getNextScheduledDate().atZone(ZoneId.systemDefault()).toOffsetDateTime().toString());
                    boolean isRunning = task == running;
                    t.put("running", isRunning);
                    if (isRunning && runningSince != null) {
                        t.put("runningSince", runningSince.toInstant().toString());
                    }
                    tasks.add(t);
                }
            }
        }
        ret.put("configurations", tasks);
        return ret;
    }
}
