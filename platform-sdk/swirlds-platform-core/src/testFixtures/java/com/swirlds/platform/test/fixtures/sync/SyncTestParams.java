/*
 * Copyright (C) 2021-2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.fixtures.sync;

import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.platform.event.AncientMode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Data holder for parameters needed for every sync unit test.
 */
public class SyncTestParams {

    private final int numNetworkNodes;
    private final int numCommonEvents;
    private final int numCallerEvents;
    private final int numListenerEvents;
    private final Long customSeed;
    private final AncientMode ancientMode;

    public SyncTestParams(
            int numNetworkNodes,
            int numCommonEvents,
            int numCallerEvents,
            int numListenerEvents,
            Long customSeed,
            @NonNull final AncientMode ancientMode) {
        this.numNetworkNodes = numNetworkNodes;
        this.numCommonEvents = numCommonEvents;
        this.numCallerEvents = numCallerEvents;
        this.numListenerEvents = numListenerEvents;
        this.customSeed = customSeed;
        this.ancientMode = Objects.requireNonNull(ancientMode);
    }

    public SyncTestParams(
            final int numNetworkNodes,
            final int numCommonEvents,
            final int numCallerEvents,
            final int numListenerEvents,
            @NonNull final AncientMode ancientMode) {
        this(numNetworkNodes, numCommonEvents, numCallerEvents, numListenerEvents, null, ancientMode);
    }

    /**
     * The number of nodes in the network.
     */
    public int getNumNetworkNodes() {
        return numNetworkNodes;
    }

    /**
     * The number of common events to insert into each node's shadow graph.
     */
    public int getNumCommonEvents() {
        return numCommonEvents;
    }

    /**
     * The number of events to insert into the caller's shadow graph in addition to
     * {@link SyncTestParams#numCommonEvents}.
     */
    public int getNumCallerEvents() {
        return numCallerEvents;
    }

    /**
     * The number of events to insert into the listener's shadow graph in addition to
     * {@link SyncTestParams#numCommonEvents}.
     */
    public int getNumListenerEvents() {
        return numListenerEvents;
    }

    /**
     * @return the custom seed set for this test, returns null if none is set
     */
    public Long getCustomSeed() {
        return customSeed;
    }

    /**
     * @return the ancient mode set for this test
     */
    public AncientMode getAncientMode() {
        return ancientMode;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("numNetworkNodes", numNetworkNodes)
                .append("numCommonEvents", numCommonEvents)
                .append("numCallerEvents", numCallerEvents)
                .append("numListenerEvents", numListenerEvents)
                .append("customSeed", customSeed)
                .append("ancientMode", ancientMode)
                .toString();
    }
}
