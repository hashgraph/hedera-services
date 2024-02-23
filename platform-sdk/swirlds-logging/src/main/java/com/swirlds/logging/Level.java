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

package com.swirlds.logging;

import com.swirlds.logging.extensions.emergency.EmergencyLogger;
import com.swirlds.logging.extensions.emergency.EmergencyLoggerProvider;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The level of a log message
 */
public enum Level {
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
     * Constructs a level
     *
     * @param levelOrdinal the ordinal of the level
     */
    Level(final int levelOrdinal) {
        this.levelOrdinal = levelOrdinal;
    }

    /**
     * Returns true if the logging of the given level is enabled
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
}
