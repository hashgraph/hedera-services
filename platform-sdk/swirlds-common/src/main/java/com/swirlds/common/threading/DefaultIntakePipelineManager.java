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

package com.swirlds.common.threading;

import com.swirlds.common.system.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default implementation of {@link IntakePipelineManager}.
 * <p>
 * Tracks the number of events from each peer that have been added to the intake pipeline, but haven't yet made it
 * through.
 */
public class DefaultIntakePipelineManager implements IntakePipelineManager {
    /**
     * A map from node id to an atomic integer, which represents the number of events from that peer that have been
     * added to the intake pipeline, but haven't yet made it through.
     */
    final Map<NodeId, AtomicInteger> unprocessedEventCounts;

    /**
     * Constructor
     */
    public DefaultIntakePipelineManager() {
        this.unprocessedEventCounts = new HashMap<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void eventAddedToIntakePipeline(@Nullable final NodeId eventSender) {
        // This can happen if the event was created locally, or obtained in any way apart from normal gossip
        if (eventSender == null) {
            return;
        }

        unprocessedEventCounts
                .computeIfAbsent(eventSender, nodeId -> new AtomicInteger(0))
                .incrementAndGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void eventThroughIntakePipeline(@Nullable final NodeId eventSender) {
        // This can happen if the event was created locally, or obtained in any way apart from normal gossip
        if (eventSender == null) {
            return;
        }

        if (!unprocessedEventCounts.containsKey(eventSender)) {
            throw new IllegalStateException(
                    "Event processed from peer %s, which hasn't sent any events. This shouldn't be possible."
                            .formatted(eventSender));
        }

        if (unprocessedEventCounts.get(eventSender).getAndUpdate(count -> count > 0 ? count - 1 : 0) == 0) {
            throw new IllegalStateException(
                    "Event processed from peer %s, but no events from that peer are in the intake pipeline. This shouldn't be possible."
                            .formatted(eventSender));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasUnprocessedEvents(@NonNull final NodeId peer) {
        Objects.requireNonNull(peer);

        return unprocessedEventCounts
                        .computeIfAbsent(peer, nodeId -> new AtomicInteger(0))
                        .get()
                > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset() {
        unprocessedEventCounts.clear();
    }
}
