package org.endeavourhealth.sftpreader.web;

import org.endeavourhealth.sftpreader.Configuration;
import org.endeavourhealth.sftpreader.SftpReaderTaskScheduler;

import java.time.Instant;

/**
 * State of the SFTP Reader running inside this webapp, for {@link StatusEndpoint}.
 */
public final class SftpReaderRuntime {

    public enum State {
        /** listener has not run yet */
        NOT_STARTED,
        /** no instance name configured - nothing to run */
        NOT_CONFIGURED,
        STARTING,
        RUNNING,
        STOPPING,
        STOPPED,
        /** startup failed or the scheduler stopped with an error - see {@link #getError()} and the log */
        FAILED
    }

    private static volatile State state = State.NOT_STARTED;
    private static volatile String instanceName;
    private static volatile String error;
    private static volatile Instant stateChanged = Instant.now();
    private static volatile Configuration configuration;
    private static volatile SftpReaderTaskScheduler scheduler;

    private SftpReaderRuntime() {
    }

    static void setState(State newState, String newError) {
        state = newState;
        error = newError;
        stateChanged = Instant.now();
    }

    static void setInstanceName(String name) {
        instanceName = name;
    }

    static void setRunning(Configuration config, SftpReaderTaskScheduler taskScheduler) {
        configuration = config;
        scheduler = taskScheduler;
    }

    static void reset() {
        configuration = null;
        scheduler = null;
        instanceName = null;
        setState(State.NOT_STARTED, null);
    }

    public static State getState() {
        return state;
    }

    public static String getInstanceName() {
        return instanceName;
    }

    public static String getError() {
        return error;
    }

    public static Instant getStateChanged() {
        return stateChanged;
    }

    public static Configuration getConfiguration() {
        return configuration;
    }

    public static SftpReaderTaskScheduler getScheduler() {
        return scheduler;
    }
}
