/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.chatter;

import com.swirlds.common.sequence.Shiftable;
import com.swirlds.common.sequence.set.ConcurrentSequenceSet;
import com.swirlds.common.sequence.set.SequenceSet;
import com.swirlds.common.system.events.EventDescriptor;
import com.swirlds.platform.gossip.chatter.protocol.MessageHandler;
import com.swirlds.platform.gossip.chatter.protocol.messages.ChatterEvent;
import java.util.List;

public class EventDedup implements MessageHandler<ChatterEvent>, Shiftable {
    private final List<MessageHandler<ChatterEvent>> handlers;
    private final SequenceSet<EventDescriptor> knownEvents =
            new ConcurrentSequenceSet<>(0, 100_000, EventDescriptor::getGeneration);
    private long oldestEvent = 0;

    public EventDedup(final List<MessageHandler<ChatterEvent>> handlers) {
        this.handlers = handlers;
    }

    @Override
    public void handleMessage(ChatterEvent event) {
        if (event.getGeneration() < oldestEvent) {
            throw new RuntimeException("received an old event");
        }
        if (knownEvents.add(event.getDescriptor())) {
            for (MessageHandler<ChatterEvent> handler : handlers) {
                handler.handleMessage(event);
            }
        } // else duplicate
    }

    @Override
    public void shiftWindow(long firstSequenceNumberInWindow) {
        knownEvents.shiftWindow(firstSequenceNumberInWindow);
        oldestEvent = firstSequenceNumberInWindow;
    }
}
