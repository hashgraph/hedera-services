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

import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.extensions.event.LogEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * A mirror of the logging system that can be used to check the logging events that were generated during a test. A
 * mirror is automatically cleared before and after a test. By doing so a mirror always contains log events of the
 * current test. To use the class a test must be annotated with {@link WithLoggingMirror}.
 *
 * @see WithLoggingMirror
 */
public interface LoggingMirror extends AutoCloseable {

    /**
     * Returns the number of log events that were generated during a test.
     *
     * @return the number of log events that were generated during a test
     */
    int getEventCount();

    @Override
    default void close() throws Exception {
        dispose();
    }

    /**
     * Clears the mirror and disposes it. This method is automatically called before and after a test.
     */
    void dispose();

    /**
     * Returns a mirror that only contains log events with the given level.
     *
     * @param level the level to filter by
     * @return a mirror that only contains log events with the given level
     */
    LoggingMirror filterByLevel(@NonNull final Level level);

    /**
     * Returns a mirror that only contains log events with the given context.
     *
     * @param key   the key of the context
     * @param value the value of the context
     * @return a mirror that only contains log events with the given context
     */
    LoggingMirror filterByContext(@NonNull final String key, @NonNull final String value);

    /**
     * Returns a mirror that only contains log events with the current thread.
     *
     * @return a mirror that only contains log events with the current thread
     */
    default LoggingMirror filterByCurrentThread() {
        return filterByThread(Thread.currentThread().getName());
    }

    /**
     * Returns a mirror that only contains log events with the given thread.
     *
     * @param threadName the name of the thread
     * @return a mirror that only contains log events with the given thread
     */
    LoggingMirror filterByThread(@NonNull final String threadName);

    /**
     * Returns a mirror that only contains log events with the given logger name (based on the class name).
     *
     * @param clazz the class to filter by
     * @return a mirror that only contains log events with the given logger name
     */
    default LoggingMirror filterByLogger(@NonNull final Class<?> clazz) {
        return filterByLogger(clazz.getName());
    }

    /**
     * Returns a mirror that only contains log events with the given logger name.
     *
     * @param loggerName the logger name to filter by
     * @return a mirror that only contains log events with the given logger name
     */
    LoggingMirror filterByLogger(@NonNull final String loggerName);

    /**
     * Returns a list of all log events that were generated during a test.
     *
     * @return a list of all log events that were generated during a test
     */
    List<LogEvent> getEvents();
}
