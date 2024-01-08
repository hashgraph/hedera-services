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
import com.swirlds.logging.test.fixtures.LoggingMirror;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.AbstractList;
import java.util.List;
import java.util.function.Function;

/**
 * A concrete implementation of the {@link LoggingMirror} interface that represents a filtered view
 * of log events based on a provided filter function. This class extends {@link AbstractLoggingMirror}
 * and allows you to create a filtered mirror of log events.
 */
public class FilteredLoggingMirror extends AbstractLoggingMirror {

    private final Function<LogEvent, Boolean> filter;
    private final List<LogEvent> list;
    private final Runnable disposeAction;

    /**
     * Constructs a new {@code FilteredLoggingMirror} instance with the specified parameters.
     *
     * @param list          The list of log events to filter.
     * @param filter        The filter function used to select log events.
     * @param disposeAction The action to be executed when this mirror is disposed.
     */
    public FilteredLoggingMirror(
            @NonNull final List<LogEvent> list,
            @NonNull final Function<LogEvent, Boolean> filter,
            @NonNull final Runnable disposeAction) {
        this.list = list;
        this.filter = filter;
        this.disposeAction = disposeAction;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public List<LogEvent> getEvents() {
        return list.stream().filter(filter::apply).toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    protected LoggingMirror filter(@NonNull final Function<LogEvent, Boolean> filter) {
        final List<LogEvent> liveList = new AbstractList<>() {
            @Override
            public int size() {
                return list.size();
            }

            @Override
            public LogEvent get(int index) {
                return list.get(index);
            }
        };
        return new FilteredLoggingMirror(liveList, filter, disposeAction);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dispose() {
        disposeAction.run();
    }
}
