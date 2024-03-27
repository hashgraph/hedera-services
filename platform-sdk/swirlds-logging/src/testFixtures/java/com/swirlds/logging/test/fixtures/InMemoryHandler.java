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

package com.swirlds.logging.test.fixtures;

import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.extensions.event.LogEvent;
import com.swirlds.logging.api.extensions.handler.AbstractLogHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A simple implementation of the {@code LogHandler} interface that stores log events in memory.
 *
 * <p>
 * The {@code InMemoryHandler} class is a basic log handler that keeps a record of log events
 * in an in-memory list. It implements the {@code LogHandler} interface to provide a simple way
 * to capture and retrieve log events during the application's execution.
 * </p>
 *
 * <p>
 * Each log event accepted by this handler is added to an internal list. You can later retrieve
 * the stored log events using the {@code getEvents} method, which returns an unmodifiable list
 * of log events.
 * </p>
 *
 * <p>
 * Note that this handler does not provide any advanced logging capabilities, such as log rotation,
 * persistence, or remote logging. It is primarily intended for debugging and testing purposes
 * where you need to capture log events in memory for inspection.
 * </p>
 */
public class InMemoryHandler extends AbstractLogHandler {

    private final List<LogEvent> events = new CopyOnWriteArrayList<>();

    /**
     * Creates a new log handler.
     *
     * @param configuration the configuration
     */
    public InMemoryHandler(final Configuration configuration) {
        super("inMemory", configuration);
    }

    /**
     * InMemoryHandler is always enabled.
     *
     * @return {@code true} to indicate that this log handler is always active
     */
    @Override
    public boolean isActive() {
        return true;
    }

    /**
     * Accepts a log event and stores it in memory.
     *
     * @param event the log event to be accepted and recorded
     */
    @Override
    public void accept(@NonNull LogEvent event) {
        events.add(event);
    }

    /**
     * Retrieves an unmodifiable list of the log events stored in memory.
     *
     * @return an unmodifiable list of log events recorded by this handler
     */
    public List<LogEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }
}
