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

/**
 * One component in an event processing pipeline. The first component in the pipeline is provided an event and can
 * decide if or when to pass that event on to the next component in the pipeline. These components can be used to track
 * events, create bottlenecks, etc.
 *
 * @param <T>
 */
public interface SimulatedEventPipeline<T extends ChatterEvent> {

    /**
     * Add an event to this pipeline component
     *
     * @param event the event to add
     */
    void addEvent(final T event);

    /**
     * Handle any events that should be handled, then invoke the next component in the pipeline
     *
     * @param core the instance of core for this node
     */
    void maybeHandleEventsAndCallNext(final ChatterCore<T> core);

    /**
     * Prints the status and/or results of this event component and calls the next component in the pipeline
     */
    void printResultsAndCallNext();

    /**
     * Sets the next event component
     *
     * @param next the next event component
     */
    void setNext(final SimulatedEventPipeline<T> next);
}
