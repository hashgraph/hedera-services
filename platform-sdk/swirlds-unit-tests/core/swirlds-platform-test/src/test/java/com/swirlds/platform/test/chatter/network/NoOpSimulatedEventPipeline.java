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

package com.swirlds.platform.test.chatter.network;

import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.chatter.protocol.ChatterCore;
import com.swirlds.platform.test.chatter.network.framework.SimulatedEventPipeline;
import com.swirlds.platform.test.simulated.config.NodeConfig;

/**
 * An event pipeline component that does nothing
 *
 */
public class NoOpSimulatedEventPipeline implements SimulatedEventPipeline {

    @Override
    public void addEvent(final GossipEvent event) {}

    @Override
    public void maybeHandleEventsAndCallNext(final ChatterCore core) {}

    @Override
    public void applyNodeConfigAndCallNext(final NodeConfig nodeConfig) {}

    @Override
    public void printCurrentStateAndCallNext() {}

    @Override
    public void setNext(final SimulatedEventPipeline next) {}

    @Override
    public SimulatedEventPipeline getNext() {
        return null;
    }
}
