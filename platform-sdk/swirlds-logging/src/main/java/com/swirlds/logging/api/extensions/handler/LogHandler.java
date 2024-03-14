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

package com.swirlds.logging.api.extensions.handler;

import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.extensions.event.LogEventConsumer;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A log handler that handles log events. A log handler can be used to write log events to a file, send them to a remote
 * server, or do any other kind of processing. A log handler is created by a {@link LogHandlerFactory} that use the Java
 * SPI.
 *
 * @see LogHandlerFactory
 */
public interface LogHandler extends LogEventConsumer {
    String PROPERTY_HANDLER = "logging.handler.%s";
    String PROPERTY_HANDLER_ENABLED = PROPERTY_HANDLER + ".enabled";

    /**
     * Returns the name of the log handler.
     *
     * @return the name of the log handler
     */
    @NonNull
    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * Returns true if the log handler is active, false otherwise. If the log handler is not active, it will not be
     * used. This can be used to disable a log handler without removing it from the configuration. The current logging
     * implementation checks that state at startup and not for every log event.
     *
     * @return true if the log handler is active, false otherwise
     */
    default boolean isActive() {
        return true;
    }

    /**
     * Calling that method will stop the log handler and finalize it. This can be used to close files or flush streams.
     */
    default void stopAndFinalize() {}

    /**
     * Updates the log handler with the new configuration.
     *
     * @param configuration the new configuration
     */
    default void update(@NonNull final Configuration configuration) {}
}
