/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.gui;

import com.swirlds.platform.event.PlatformEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;

/**
 * Provides events for the GUI by using a list of events
 */
public class ListEventProvider implements GuiEventProvider {
    private final List<PlatformEvent> events;
    private int index;

    /**
     * Constructor
     *
     * @param events the list of events
     */
    public ListEventProvider(@NonNull final List<PlatformEvent> events) {
        Objects.requireNonNull(events);
        this.events = events;
        this.index = 0;
    }

    @Override
    public @NonNull List<PlatformEvent> provideEvents(final int numberOfEvents) {
        if (index >= events.size()) {
            return List.of();
        }
        final int toIndex = Math.min(index + numberOfEvents, events.size());
        final List<PlatformEvent> list = events.subList(index, toIndex);
        index = toIndex;
        return list;
    }

    @Override
    public void reset() {
        index = 0;
    }
}
