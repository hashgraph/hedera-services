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

package com.hedera.service.gossip.impl;

import com.hedera.service.gossip.IntakeEventCounter;
import com.swirlds.common.AddressBook;
import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

/**
 * Default implementation of {@link IntakeEventCounter}.
 * <p>
 * Tracks the number of events from each peer that have been added to the intake pipeline, but haven't yet made it
 * through.
 */
public class DefaultIntakeEventCounter implements IntakeEventCounter {
    /**
     * Lambda to update the intake counter when an event exits the intake pipeline. The lambda decrements the counter,
     * but prevents it from going below 0.
     */
    private static final IntUnaryOperator EXIT_INTAKE = count -> {
        if (count > 0) {
            return count - 1;
        } else {
            throw new IllegalStateException(
                    "Event processed from peer, but no events from that peer were in the intake pipeline. "
                            + "This shouldn't be possible.");
        }
    };

    /**
     * A map from node id to an atomic integer, which represents the number of events from that peer that have been
     * added to the intake pipeline, but haven't yet made it through.
     */
    private final Map<NodeId, AtomicInteger> unprocessedEventCounts;

    /**
     * Constructor.
     *
     * @param addressBook the address book
     */
    public DefaultIntakeEventCounter(@NonNull final AddressBook addressBook) {
        this.unprocessedEventCounts = new HashMap<>();

        for (final NodeId nodeId : addressBook.getNodeIdSet()) {
            unprocessedEventCounts.put(nodeId, new AtomicInteger(0));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasUnprocessedEvents(@NonNull final NodeId peer) {
        Objects.requireNonNull(peer);

        return unprocessedEventCounts.get(peer).get() > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void eventEnteredIntakePipeline(@NonNull final NodeId peer) {
        Objects.requireNonNull(peer);

        unprocessedEventCounts.get(peer).incrementAndGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void eventExitedIntakePipeline(@Nullable final NodeId peer) {
        if (peer == null) {
            // this will happen if the event wasn't received through normal gossip
            return;
        }

        unprocessedEventCounts.get(peer).getAndUpdate(EXIT_INTAKE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset() {
        unprocessedEventCounts.forEach((nodeId, unprocessedEventCount) -> unprocessedEventCount.set(0));
    }
}
