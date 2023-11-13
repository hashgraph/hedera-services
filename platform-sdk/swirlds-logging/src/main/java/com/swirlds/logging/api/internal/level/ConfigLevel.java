/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
    public boolean enabledLoggingOfLevel(@NonNull final Level level) {
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
