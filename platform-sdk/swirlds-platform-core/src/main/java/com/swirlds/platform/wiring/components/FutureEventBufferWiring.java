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
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.FutureEventBuffer;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * The wiring for the {@link com.swirlds.platform.event.FutureEventBuffer}.
 *
 * @param eventInput       an input wire with events in topological order, possibly containing time travelers
 * @param eventWindowInput input wire with event windows
 * @param eventOutput      an output wire with events in topological order, guaranteed no time travelers
 * @param flushRunnable    a runnable that will flush the future event buffer
 */
public record FutureEventBufferWiring(
        @NonNull InputWire<GossipEvent> eventInput,
        @NonNull InputWire<NonAncientEventWindow> eventWindowInput,
        @NonNull OutputWire<GossipEvent> eventOutput,
        @NonNull Runnable flushRunnable) {

    /**
     * Create a new instance of the FutureEventBufferWiring.
     *
     * @param scheduler the scheduler that will run the future event buffer
     * @return a new instance of the FutureEventBufferWiring
     */
    @NonNull
    public static FutureEventBufferWiring create(@NonNull final TaskScheduler<List<GossipEvent>> scheduler) {
        final InputWire<GossipEvent> eventInput = scheduler.buildInputWire("preconsensus events");
        final InputWire<NonAncientEventWindow> eventWindowInput = scheduler.buildInputWire("non-ancient event window");

        final OutputWire<List<GossipEvent>> eventListOutputWire = scheduler.getOutputWire();
        final OutputWire<GossipEvent> eventOutputWire =
                eventListOutputWire.buildSplitter("futureEventBufferSplitter", "possible parent lists");

        final Runnable flushRunnable = scheduler::flush;

        return new FutureEventBufferWiring(eventInput, eventWindowInput, eventOutputWire, flushRunnable);
    }

    /**
     * Bind to the future event buffer.
     *
     * @param futureEventBuffer the future event buffer to bind
     */
    public void bind(@NonNull final FutureEventBuffer futureEventBuffer) {
        ((BindableInputWire<GossipEvent, List<GossipEvent>>) eventInput).bind(futureEventBuffer::addEvent);
        ((BindableInputWire<NonAncientEventWindow, NonAncientEventWindow>) eventWindowInput)
                .bind(futureEventBuffer::updateEventWindow);
    }
}
