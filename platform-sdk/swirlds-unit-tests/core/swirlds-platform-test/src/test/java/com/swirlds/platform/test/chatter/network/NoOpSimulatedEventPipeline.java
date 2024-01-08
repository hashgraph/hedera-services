/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.swirlds.platform.gossip.chatter.protocol.ChatterCore;
import com.swirlds.platform.gossip.chatter.protocol.messages.ChatterEvent;
import com.swirlds.platform.test.chatter.network.framework.SimulatedEventPipeline;
import com.swirlds.platform.test.simulated.config.NodeConfig;

/**
 * An event pipeline component that does nothing
 *
 * @param <T> the type of event this pipeline component uses
 */
public class NoOpSimulatedEventPipeline<T extends ChatterEvent> implements SimulatedEventPipeline<T> {

    @Override
    public void addEvent(final T event) {}

    @Override
    public void maybeHandleEventsAndCallNext(final ChatterCore<T> core) {}

    @Override
    public void applyNodeConfigAndCallNext(final NodeConfig nodeConfig) {}

    @Override
    public void printCurrentStateAndCallNext() {}

    @Override
    public void setNext(final SimulatedEventPipeline<T> next) {}

    @Override
    public SimulatedEventPipeline<T> getNext() {
        return null;
    }
}
