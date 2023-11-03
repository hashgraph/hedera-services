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

package com.swirlds.platform.wiring;

import com.swirlds.common.wiring.InputWire;
import com.swirlds.common.wiring.OutputWire;
import com.swirlds.common.wiring.TaskScheduler;
import com.swirlds.common.wiring.WiringModel;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.orphan.OrphanBuffer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

// Future work: it may actually make more sense to colocate the scheduler classes with the implementations.
//              This decision can be delayed until we begin migration in earnest.

/**
 * Wiring for the {@link OrphanBuffer}.
 */
public class OrphanBufferScheduler {

    private final InputWire<GossipEvent, List<GossipEvent>> eventInput;
    private final InputWire<Long, List<GossipEvent>> minimumGenerationNonAncientInput;

    private final OutputWire<GossipEvent> eventOutput;

    /**
     * Constructor.
     *
     * @param model the wiring model
     */
    public OrphanBufferScheduler(@NonNull final WiringModel model) {
        final TaskScheduler<List<GossipEvent>> taskScheduler = model.schedulerBuilder("orphanBuffer")
                .withConcurrency(false)
                .withUnhandledTaskCapacity(500)
                .withFlushingEnabled(true)
                .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                .build()
                .cast();

        eventInput = taskScheduler.buildInputWire("unordered events");
        minimumGenerationNonAncientInput = taskScheduler.buildInputWire("minimum generation non ancient");

        eventOutput = taskScheduler.getOutputWire().buildSplitter();
    }

    /**
     * Passes events to the orphan buffer.
     *
     * @return the event input channel
     */
    @NonNull
    public InputWire<GossipEvent, List<GossipEvent>> getEventInput() {
        return eventInput;
    }

    /**
     * Passes the minimum generation non ancient to the orphan buffer.
     *
     * @return the minimum generation non ancient input channel
     */
    @NonNull
    public InputWire<Long, List<GossipEvent>> getMinimumGenerationNonAncientInput() {
        return minimumGenerationNonAncientInput;
    }

    /**
     * Get the output of the orphan buffer, i.e. a stream of events in topological order.
     *
     * @return the event output channel
     */
    @NonNull
    public OutputWire<GossipEvent> getEventOutput() {
        return eventOutput;
    }

    /**
     * Bind an orphan buffer to this wiring.
     *
     * @param orphanBuffer the orphan buffer to bind
     */
    public void bind(@NonNull final OrphanBuffer orphanBuffer) {
        // Future work: these handlers currently do not return anything. They need to be refactored so that
        // they return a list of events (as opposed to passing them to a handler lambda).

        eventInput.bind(orphanBuffer::handleEvent);
        minimumGenerationNonAncientInput.bind(orphanBuffer::setMinimumGenerationNonAncient);
    }
}
