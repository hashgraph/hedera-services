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

import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.extensions.event.LogEvent;
import com.swirlds.logging.test.fixtures.LoggingMirror;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Function;

/**
 * An abstract base class that implements the {@link LoggingMirror} interface
 * and provides common filtering operations for log events.
 */
public abstract class AbstractLoggingMirror implements LoggingMirror {

    /**
     * Creates a new instance of a logging mirror that filters log events based on
     * the provided filter function.
     *
     * @param filter The filter function to apply to log events.
     * @return A new logging mirror instance with the specified filter applied.
     */
    protected abstract LoggingMirror filter(Function<LogEvent, Boolean> filter);

    /**
     * {@inheritDoc}
     */
    @Override
    public int getEventCount() {
        return getEvents().size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public LoggingMirror filterByLevel(@NonNull final Level level) {
        Function<LogEvent, Boolean> filter = event -> event.level().ordinal() >= level.ordinal();
        return filter(filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public LoggingMirror filterByContext(@NonNull final String key, @NonNull final String value) {
        Function<LogEvent, Boolean> filter = event ->
                event.context().containsKey(key) && event.context().get(key).equals(value);
        return filter(filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public LoggingMirror filterByThread(@NonNull final String threadName) {
        Function<LogEvent, Boolean> filter = event -> Objects.equals(event.threadName(), threadName);
        return filter(filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public LoggingMirror filterByLogger(@NonNull final String loggerName) {
        Function<LogEvent, Boolean> filter = event -> event.loggerName().startsWith(loggerName);
        return filter(filter);
    }
}
