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

package com.swirlds.platform.gossip;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default implementation of {@link IntakeEventCounter}.
 * <p>
 * Tracks the number of events from each peer that have been added to the intake pipeline, but haven't yet made it
 * through.
 */
public class DefaultIntakeEventCounter implements IntakeEventCounter {
    /**
     * A map from node id to an atomic integer, which represents the number of events from that peer that have been
     * added to the intake pipeline, but haven't yet made it through.
     */
    private final Map<NodeId, AtomicInteger> unprocessedEventCounts;

    /**
     * Constructor
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
    @NonNull
    @Override
    public AtomicInteger getPeerCounter(@NonNull NodeId peer) {
        Objects.requireNonNull(peer);

        return unprocessedEventCounts.get(peer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset() {
        unprocessedEventCounts.forEach((nodeId, unprocessedEventCount) -> unprocessedEventCount.set(0));
    }
}
