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

package com.swirlds.platform.wiring.components;

import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.gossip.shadowgraph.Shadowgraph;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for the {@link Shadowgraph}.
 *
 * @param eventInput       the input wire for events to be added to the shadow graph
 * @param eventWindowInput the input wire for the non-expired event window
 * @param flushRunnable    the runnable to flush the task scheduler
 */
public record ShadowgraphWiring(
        @NonNull InputWire<EventImpl> eventInput,
        @NonNull InputWire<NonAncientEventWindow> eventWindowInput,
        @NonNull Runnable flushRunnable) {

    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler for this wiring
     * @return the new wiring instance
     */
    public static ShadowgraphWiring create(@NonNull final TaskScheduler<Void> taskScheduler) {

        return new ShadowgraphWiring(
                taskScheduler.buildInputWire("events to gossip"),
                taskScheduler.buildInputWire("non-ancient event window"),
                taskScheduler::flush);
    }

    /**
     * Bind a shadowgraph to this wiring.
     *
     * @param shadowgraph the shadow graph to bind
     */
    public void bind(@NonNull final Shadowgraph shadowgraph) {
        ((BindableInputWire<EventImpl, Void>) eventInput).bindConsumer(shadowgraph::addEvent);
        ((BindableInputWire<NonAncientEventWindow, Void>) eventWindowInput)
                .bindConsumer(shadowgraph::updateEventWindow);
    }
}
