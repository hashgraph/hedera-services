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

package com.swirlds.platform.test.chatter.network;

import com.swirlds.platform.chatter.protocol.ChatterCore;
import com.swirlds.platform.chatter.protocol.messages.ChatterEvent;

public abstract class AbstractSimulatedEventPipeline<T extends ChatterEvent> implements SimulatedEventPipeline<T> {

    protected SimulatedEventPipeline<T> next;

    /**
     * Handle events previously added to the component with {@link #addEvent(ChatterEvent)}, if they should be handled.
     *
     * @param core the instance of core for this node used to handle events
     */
    abstract void maybeHandleEvents(final ChatterCore<T> core);

    abstract void printResults();

    @Override
    public void setNext(final SimulatedEventPipeline<T> next) {
        this.next = next;
    }

    @Override
    public void maybeHandleEventsAndCallNext(final ChatterCore<T> core) {
        maybeHandleEvents(core);
        if (next != null) {
            next.maybeHandleEventsAndCallNext(core);
        }
    }

    @Override
    public void printResultsAndCallNext() {
        printResults();
        if (next != null) {
            next.printResultsAndCallNext();
        }
    }
}
