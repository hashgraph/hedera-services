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

package com.swirlds.logging.api.extensions.event;

import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.Marker;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;

/**
 * A log event that is passed to the {@link LogEventConsumer} for processing. Normally log events are created by a
 * {@link com.swirlds.logging.api.Logger} or a {@link com.swirlds.logging.api.extensions.provider.LogProvider}
 */
public interface LogEvent {

    /**
     * Returns the log level.
     *
     * @return the log level
     */
    @NonNull
    Level level();

    /**
     * Returns the name of the logger.
     *
     * @return the name of the logger
     */
    @NonNull
    String loggerName();

    /**
     * Returns the name of the thread on that the event has been created.
     *
     * @return the name of the thread
     */
    @NonNull
    String threadName();

    /**
     * Returns the timestamp of the creation of the log event (in ms).
     *
     * @return the timestamp
     */
    long timestamp();

    /**
     * Returns the log message.
     *
     * @return the log message
     */
    @NonNull
    LogMessage message();

    /**
     * Returns the throwable.
     *
     * @return the throwable
     */
    @Nullable
    Throwable throwable();

    /**
     * Returns the marker.
     *
     * @return the marker
     */
    @Nullable
    Marker marker();

    /**
     * Returns the context.
     *
     * @return the context
     */
    @NonNull
    Map<String, String> context();
}
