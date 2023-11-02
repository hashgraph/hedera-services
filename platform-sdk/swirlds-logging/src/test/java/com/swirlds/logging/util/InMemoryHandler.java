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

package com.swirlds.logging.util;

import com.swirlds.logging.api.extensions.event.LogEvent;
import com.swirlds.logging.api.extensions.handler.LogHandler;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryHandler implements LogHandler {

    private final List<LogEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public String getName() {
        return InMemoryHandler.class.getSimpleName();
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public void accept(LogEvent event) {
        events.add(event);
    }

    public List<LogEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }
}
