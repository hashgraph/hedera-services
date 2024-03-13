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

package com.swirlds.logging.test.fixtures.internal;

import com.swirlds.logging.api.extensions.event.LogEvent;
import com.swirlds.logging.api.extensions.handler.LogHandler;
import com.swirlds.logging.api.internal.DefaultLoggingSystem;
import com.swirlds.logging.test.fixtures.LoggingMirror;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * A concrete implementation of the {@link LoggingMirror} interface that serves as a logging mirror and also implements
 * the {@link LogHandler} interface to receive and store log events.
 */
public class LoggingMirrorImpl implements LoggingMirror, LogHandler {

    private final List<LogEvent> events = new CopyOnWriteArrayList<>();

    /**
     * Constructs a new {@code LoggingMirrorImpl} instance. It registers itself as a log handler with the default
     * logging system to receive log events.
     */
    public LoggingMirrorImpl() {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void accept(@NonNull final LogEvent event) {
        events.add(event);
    }

    /**
     * Clears the mirror and disposes it. This method is automatically called before and after a test.
     */
    @Override
    public void close() {
        DefaultLoggingSystem.getInstance().removeHandler(this);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public LoggingMirror filter(@NonNull final Predicate<LogEvent> filter) {
        return new FilteredLoggingMirror(events, filter, this::close);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<LogEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getName() {
        return "LoggingMirror";
    }
}
