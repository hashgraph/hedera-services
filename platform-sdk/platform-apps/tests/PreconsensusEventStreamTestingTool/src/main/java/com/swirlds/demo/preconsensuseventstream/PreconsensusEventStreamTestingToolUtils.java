package com.swirlds.demo.preconsensuseventstream;

import java.io.File;

/**
 * Utility class for the {@link PreconsensusEventStreamTestingToolMain} app
 */
public final class PreconsensusEventStreamTestingToolUtils {
    /**
     * The name of the log file being written to / read from
     */
    public static final String LOG_FILE_NAME = "PreconsensusEventStreamTestLog";

    /**
     * Hidden constructor
     */
    private PreconsensusEventStreamTestingToolUtils() {
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
