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

package com.swirlds.platform.test.fixtures.turtle.gossip;

import com.swirlds.common.event.PlatformEvent;
import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * An event that is in transit between nodes in the network.
 *
 * @param event       the event being transmitted
 * @param sender      the node that sent the event
 * @param arrivalTime the time the event is scheduled to arrive at its destination
 */
public record EventInTransit(@NonNull PlatformEvent event, @NonNull NodeId sender, @NonNull Instant arrivalTime)
        implements Comparable<EventInTransit> {
    @Override
    public int compareTo(@NonNull final EventInTransit that) {
        return arrivalTime.compareTo(that.arrivalTime);
    }
}
