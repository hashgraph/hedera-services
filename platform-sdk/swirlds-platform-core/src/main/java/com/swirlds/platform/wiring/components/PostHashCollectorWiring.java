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
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring object that allows for the staging of events that have been hashed, but haven't been passed further down the
 * pipeline yet.
 * <p>
 * This wiring object is a workaround for the problems that concurrent schedulers currently have with pushing to
 * components that apply backpressure.
 *
 * @param eventInput    the input wire for events that have been hashed
 * @param eventOutput   the output wire for events to be passed further along the pipeline
 * @param flushRunnable the runnable to flush the collector
 */
public record PostHashCollectorWiring(
        @NonNull InputWire<GossipEvent> eventInput,
        @NonNull OutputWire<GossipEvent> eventOutput,
        @NonNull Runnable flushRunnable) {

    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler for this wiring
     * @return the new wiring instance
     */
    public static PostHashCollectorWiring create(@NonNull final TaskScheduler<GossipEvent> taskScheduler) {
        final BindableInputWire<GossipEvent, GossipEvent> inputWire = taskScheduler.buildInputWire("hashed events");

        // don't do anything to the event. The purpose of this wiring is to simply stage hashed events until the next
        // component in the pipeline is ready to receive them
        inputWire.bind(hashedEvent -> hashedEvent);

        return new PostHashCollectorWiring(inputWire, taskScheduler.getOutputWire(), taskScheduler::flush);
    }
}
