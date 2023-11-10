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
import com.swirlds.platform.event.validation.InternalEventValidator;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for the {@link com.swirlds.platform.event.validation.InternalEventValidator InternalEventValidator}.
 */
public class InternalEventValidatorScheduler {
    private final TaskScheduler<GossipEvent> taskScheduler;
    private final InputWire<GossipEvent, GossipEvent> eventInput;

    /**
     * Constructor.
     *
     * @param model the wiring model
     */
    public InternalEventValidatorScheduler(@NonNull final WiringModel model) {
        taskScheduler = model.schedulerBuilder("internalEventValidator")
                .withConcurrency(false)
                .withUnhandledTaskCapacity(500)
                .withFlushingEnabled(true)
                .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                .build()
                .cast();

        eventInput = taskScheduler.buildInputWire("non-validated events");
    }

    /**
     * Gets the event input wire
     *
     * @return the event input wire
     */
    @NonNull
    public InputWire<GossipEvent, GossipEvent> getEventInput() {
        return eventInput;
    }

    /**
     * Get the output of the internal validator, i.e. a stream of events with validated internal data.
     *
     * @return the event output channel
     */
    @NonNull
    public OutputWire<GossipEvent> getEventOutput() {
        return taskScheduler.getOutputWire();
    }

    /**
     * Bind an internal event validator to this wiring.
     *
     * @param internalEventValidator the validator to bind
     */
    public void bind(@NonNull final InternalEventValidator internalEventValidator) {
        eventInput.bind(internalEventValidator::validateEvent);
    }
}
