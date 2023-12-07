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

package com.swirlds.logging.api;

import com.swirlds.logging.api.extensions.emergency.EmergencyLogger;
import com.swirlds.logging.api.extensions.emergency.EmergencyLoggerProvider;
import com.swirlds.logging.api.internal.DefaultLoggingSystem;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This class provides access to the {@link Logger} instances. The class is the entry point to the logging system. It is
 * a factory for {@link Logger} instances. Do not mix this class up with factories for other logging libraries it does
 * not have {@code factory} in the name.
 */
public final class Loggers {

    /**
     * The emergency logger is used to log errors that occur in the logging system.
     */
    private static final EmergencyLogger EMERGENCY_LOGGER = EmergencyLoggerProvider.getEmergencyLogger();

    /**
     * Returns a {@link Logger} instance with the given name.
     *
     * @param name the name of the logger
     * @return a {@link Logger} instance with the given name
     */
    @NonNull
    public static Logger getLogger(@NonNull final String name) {
        if (name == null) {
            EMERGENCY_LOGGER.logNPE("name");
            return getLogger("");
        }
        return DefaultLoggingSystem.getInstance().getLogger(name);
    }

    /**
     * Returns a {@link Logger} instance for the given class. The name of the logger is the fully qualified name of the
     * class.
     *
     * @param clazz the class for which a logger should be returned
     * @return a {@link Logger} instance for the given class
     */
    @NonNull
    public static Logger getLogger(@NonNull final Class<?> clazz) {
        if (clazz == null) {
            EMERGENCY_LOGGER.logNPE("clazz");
            return getLogger("");
        }
        return getLogger(clazz.getName());
    }
}
