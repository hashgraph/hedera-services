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
import com.swirlds.logging.api.internal.DefaultLoggingSystem;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This class provides access to the {@link Logger} instances. The class is the entry point to the logging system. It is
 * a factory for {@link Logger} instances. To not mix it up with other logger factories we decided to use Loggers as a
 * name that is not used in any other logging framework.
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

    /**
     * Starts the logging system. This method should be called before any logging is done. It is not necessary to call
     * this method, but it is recommended to do so.
     *
     * @return {@code true} if the logging system was successfully initialized, {@code false} otherwise
     */
    public static boolean init() {
        return DefaultLoggingSystem.getInstance().isInitialized();
    }
}
