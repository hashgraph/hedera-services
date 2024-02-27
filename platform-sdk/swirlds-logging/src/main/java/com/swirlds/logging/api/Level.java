/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.logging.api;

import com.swirlds.logging.api.extensions.emergency.EmergencyLogger;
import com.swirlds.logging.api.extensions.emergency.EmergencyLoggerProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * The level of a log message
 */
public enum Level {
    OFF(0),
    ERROR(10),
    WARN(20),
    INFO(30),
    DEBUG(40),
    TRACE(100);

    /**
     * The emergency logger
     */
    private static final EmergencyLogger EMERGENCY_LOGGER = EmergencyLoggerProvider.getEmergencyLogger();

    /**
     * The ordinal of the level
     */
    private final int levelOrdinal;

    /**
     * Creates a new level with the given ordinal
     *
     * @param levelOrdinal the ordinal of the level
     */
    Level(final int levelOrdinal) {
        this.levelOrdinal = levelOrdinal;
    }

    /**
     * Returns true if the logging of the given level is enabled
     *
     * @param level the level
     * @return true if the logging of the given level is enabled
     */
    public boolean enabledLoggingOfLevel(@NonNull final Level level) {
        if (level == null) {
            EMERGENCY_LOGGER.logNPE("level");
            return true;
        }
        return this.levelOrdinal >= level.levelOrdinal;
    }

    /**
     * Returns the level for the given name or the given default level if no level can be found for the given name.
     *
     * @param value        the name of the level
     * @param defaultLevel the default level
     * @return the level for the given name
     */
    public static Level valueOfOrElse(@Nullable final String value, @NonNull final Level defaultLevel) {
        if (defaultLevel == null) {
            EMERGENCY_LOGGER.logNPE("defaultLevel");
            return valueOfOrElse(value, INFO);
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            EMERGENCY_LOGGER.log(ERROR, "Invalid log level: " + value, e);
            return defaultLevel;
        }
    }

    public String nameWithFixedSize() {
        if (this == OFF) {
            return "OFF  ";
        } else if (this == ERROR) {
            return "ERROR ";
        } else if (this == WARN) {
            return "WARN  ";
        } else if (this == INFO) {
            return "INFO  ";
        } else if (this == DEBUG) {
            return "DEBUG ";
        } else if (this == TRACE) {
            return "TRACE ";
        } else {
            return "      ";
        }
    }
}
