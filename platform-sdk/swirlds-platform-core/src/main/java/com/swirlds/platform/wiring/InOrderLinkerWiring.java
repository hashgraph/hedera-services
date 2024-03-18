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

package com.swirlds.platform.wiring;

import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.linking.InOrderLinker;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for the {@link InOrderLinker}.
 *
 * @param eventInput                 the input wire for events to be linked
 * @param nonAncientEventWindowInput the input wire for the minimum generation non-ancient
 * @param clearInput                 the input wire to clear the internal state of the linker
 * @param eventOutput                the output wire for linked events
 * @param flushRunnable              the runnable to flush the linker
 */
public record InOrderLinkerWiring(
        @NonNull InputWire<GossipEvent> eventInput,
        @NonNull InputWire<NonAncientEventWindow> nonAncientEventWindowInput,
        @NonNull InputWire<ClearTrigger> clearInput,
        @NonNull OutputWire<EventImpl> eventOutput,
        @NonNull Runnable flushRunnable) {

    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler for this linker
     * @return the new wiring instance
     */
    public static InOrderLinkerWiring create(@NonNull final TaskScheduler<EventImpl> taskScheduler) {
        return new InOrderLinkerWiring(
                taskScheduler.buildInputWire("unlinked events"),
                taskScheduler.buildInputWire("non-ancient event window"),
                taskScheduler.buildInputWire("clear"),
                taskScheduler.getOutputWire(),
                taskScheduler::flush);
    }

    /**
     * Bind an in order linker to this wiring.
     *
     * @param inOrderLinker the in order linker to bind
     */
    public void bind(@NonNull final InOrderLinker inOrderLinker) {
        ((BindableInputWire<GossipEvent, EventImpl>) eventInput).bind(inOrderLinker::linkEvent);
        ((BindableInputWire<NonAncientEventWindow, EventImpl>) nonAncientEventWindowInput)
                .bindConsumer(inOrderLinker::setNonAncientEventWindow);
        ((BindableInputWire<ClearTrigger, EventImpl>) clearInput).bindConsumer(inOrderLinker::clear);
    }
}
