package com.swirlds.logging.api.internal.level;

import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.extensions.emergency.EmergencyLogger;
import com.swirlds.logging.api.extensions.emergency.EmergencyLoggerProvider;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Internal enum to define the configuration level. Includes UNDEFINED which should not be exposed to public API.
 */
public enum ConfigLevel {
    UNDEFINED,
    OFF,
    ERROR,
    WARN,
    INFO,
    DEBUG,
    TRACE;

    /**
     * Returns true if the logging of the given level is enabled
     *
     * @param level the level
     * @return true if the logging of the given level is enabled
     */
    public boolean enabledLoggingOfLevel (@NonNull final Level level) {
        final EmergencyLogger emergencyLogger = EmergencyLoggerProvider.getEmergencyLogger();
        if (level == null) {
            emergencyLogger.logNPE("level");
            return true;
        }
        if (this == UNDEFINED) {
            emergencyLogger.log(Level.ERROR, "Undefined logging level!");
            return false;
        } else if (this == OFF) {
            return false;
        } else if (this == ERROR) {
            return Level.ERROR.enabledLoggingOfLevel(level);
        } else if (this == WARN) {
            return Level.WARN.enabledLoggingOfLevel(level);
        } else if (this == INFO) {
            return Level.INFO.enabledLoggingOfLevel(level);
        } else if (this == DEBUG) {
            return Level.DEBUG.enabledLoggingOfLevel(level);
        }
        return true;
    }
}
