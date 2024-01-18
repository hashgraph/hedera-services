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
 * This wiring object is a workaround for the following problem that concurrent schedulers currently have with pushing
 * tasks directly to components that apply backpressure:
 * <p>
 * If the component immediately following a concurrent scheduler applies backpressure, the concurrent scheduler will
 * continue doing work and parking each result, unable to pass the unit of work along. This results in a proliferation
 * of parked threads.
 * <p>
 * This wiring object represents a workaround: it is a sequential scheduler which shares a combined object counter with
 * the preceding concurrent scheduler (in this case, the hasher). Since the pair of schedulers share a counter, the
 * sequential scheduler does *not* apply backpressure to the concurrent scheduler. Instead, "finished" hashing tasks
 * will wait in the queue of the sequential scheduler until the next component in the pipeline is ready to receive them.
 * The concurrent scheduler will refuse to accept additional work based on the number of tasks that are waiting in the
 * sequential scheduler's queue.
 *
 * @param eventInput  the input wire for events that have been hashed
 * @param eventOutput the output wire for events to be passed further along the pipeline
 */
public record PostHashCollectorWiring(
        @NonNull InputWire<GossipEvent> eventInput, @NonNull OutputWire<GossipEvent> eventOutput) {

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

        return new PostHashCollectorWiring(inputWire, taskScheduler.getOutputWire());
    }
}
