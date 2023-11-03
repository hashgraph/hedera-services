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
import com.swirlds.platform.event.linking.InOrderLinker;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for the {@link com.swirlds.platform.event.linking.InOrderLinker InOrderLinker}.
 */
public class InOrderLinkerScheduler {
    private final TaskScheduler<EventImpl> taskScheduler;

    private final InputWire<GossipEvent, EventImpl> eventInput;
    private final InputWire<Long, EventImpl> minimumGenerationNonAncientInput;

    /**
     * Constructor.
     *
     * @param model the wiring model
     */
    public InOrderLinkerScheduler(@NonNull final WiringModel model) {
        taskScheduler = model.schedulerBuilder("inOrderLinker")
                .withConcurrency(false)
                .withUnhandledTaskCapacity(500)
                .withFlushingEnabled(true)
                .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                .build()
                .cast();

        eventInput = taskScheduler.buildInputWire("unlinked events");
        minimumGenerationNonAncientInput = taskScheduler.buildInputWire("minimum generation non ancient");
    }

    /**
     * Gets the event input wire.
     *
     * @return the event input wire
     */
    @NonNull
    public InputWire<GossipEvent, EventImpl> getEventInput() {
        return eventInput;
    }

    /**
     * Gets the minimum generation non ancient input wire.
     *
     * @return the minimum generation non ancient input wire
     */
    @NonNull
    public InputWire<Long, EventImpl> getMinimumGenerationNonAncientInput() {
        return minimumGenerationNonAncientInput;
    }

    /**
     * Get the output of the in order linker, i.e. a stream of linked events
     *
     * @return the event output wire
     */
    @NonNull
    public OutputWire<EventImpl> getEventOutput() {
        return taskScheduler.getOutputWire();
    }

    /**
     * Bind an in order linker to this wiring.
     *
     * @param inOrderLinker the in order linker to bind
     */
    public void bind(@NonNull final InOrderLinker inOrderLinker) {
        eventInput.bind(inOrderLinker::linkEvent);
        minimumGenerationNonAncientInput.bind(inOrderLinker::setMinimumGenerationNonAncient);
    }
}
