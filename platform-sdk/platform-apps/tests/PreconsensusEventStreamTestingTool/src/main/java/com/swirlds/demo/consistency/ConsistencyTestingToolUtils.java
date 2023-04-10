package com.swirlds.demo.consistency;

import java.io.File;

/**
 * Utility class for the {@link ConsistencyTestingToolMain} app
 */
public final class ConsistencyTestingToolUtils {
    /**
     * The name of the log file being written to / read from
     */
    public static final String LOG_FILE_NAME = "ConsistencyTestLog";

    /**
     * Hidden constructor
     */
    private ConsistencyTestingToolUtils() {
    }

    /**
     * Get the name of the log file being written to / read from during execution of the testing app
     *
     * @return the name of the log file
     */
    public static String getLogFileName() {
        return System.getProperty("user.dir") + File.separator + LOG_FILE_NAME + ".csv";
    }
}
