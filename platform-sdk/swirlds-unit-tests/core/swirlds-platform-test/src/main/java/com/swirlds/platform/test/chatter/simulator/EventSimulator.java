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

package com.swirlds.platform.test.chatter.simulator;

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Random;

public final class EventSimulator {

    private EventSimulator() {}

    /**
     * Create a new simulated event.
     *
     * @param random
     * 		a source of randomness
     * @param creator
     * 		the creator of the event
     * @param round
     * 		the round associated with the event
     * @param size
     * 		the size of the event
     */
    @NonNull
    public static GossipEvent simulateEvent(
            @NonNull final Random random, @NonNull final NodeId creator, final long round, final int size) {
        return null; // TODO implement maybe...
    }
}
