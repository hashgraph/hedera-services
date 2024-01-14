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

package com.swirlds.platform.test.sync.turbo;

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.sync.turbo.TurboSyncRunner;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Utility methods for testing turbo sync.
 */
public final class TurboSyncTestFramework {

    private TurboSyncTestFramework() {}

    /**
     * The result of allowing two nodes to run turbo sync for a short period of time.
     *
     * @param eventsReceivedA the events that peer A received in the order that it received them, if duplicate events
     *                        were received then there will be duplicates in this list
     * @param eventsReceivedB the events that peer B received in the order that it received them, if duplicate events
     *                        were received then there will be duplicates in this list
     */
    public record TestSynchronizationResult(
            @NonNull List<GossipEvent> eventsReceivedA, @NonNull List<GossipEvent> eventsReceivedB) {}

    /**
     * Simulates a turbo sync between two nodes for a period of time.
     *
     * @param eventsA the events peer A knows about at the start of the test
     * @param eventsB the events peer B knows about at the start of the test
     * @return the result of the test
     */
    @NonNull
    public static TestSynchronizationResult simulateSynchronization(
            @NonNull final Random random,
            @NonNull final AddressBook addressBook,
            @NonNull final List<GossipEvent> eventsA,
            @NonNull final List<GossipEvent> eventsB) {

        final NodeId nodeA = addressBook.getNodeId(0);
        final NodeId nodeB = addressBook.getNodeId(0);

        final Instant startingTime = Instant.ofEpochMilli(random.nextInt());

        final List<GossipEvent> eventsReceivedA = new ArrayList<>();
        final TurboSyncRunner runnerA = new TurboSyncTestNodeBuilder(startingTime, addressBook, nodeA, nodeB, null)
                .withEventConsumer(eventsReceivedA::add)
                .build();

        final List<GossipEvent> eventsReceivedB = new ArrayList<>();
        final TurboSyncRunner runnerB = new TurboSyncTestNodeBuilder(startingTime, addressBook, nodeB, nodeA, null)
                .withEventConsumer(eventsReceivedB::add)
                .build();

        return new TestSynchronizationResult(List.of(), List.of());
    }
}
