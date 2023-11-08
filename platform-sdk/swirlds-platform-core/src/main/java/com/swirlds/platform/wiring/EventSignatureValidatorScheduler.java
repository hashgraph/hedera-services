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
import com.swirlds.platform.event.validation.EventSignatureValidator;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for the event signature validator.
 */
public class EventSignatureValidatorScheduler {

    private final TaskScheduler<GossipEvent> taskScheduler;

    private final InputWire<GossipEvent, GossipEvent> eventInput;
    private final InputWire<Long, GossipEvent> minimumGenerationNonAncientInput;

    /**
     * Constructor.
     *
     * @param model the wiring model
     */
    public EventSignatureValidatorScheduler(@NonNull final WiringModel model) {
        taskScheduler = model.schedulerBuilder("eventSignatureValidator")
                .withConcurrency(false)
                .withUnhandledTaskCapacity(500)
                .withFlushingEnabled(true)
                .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                .build()
                .cast();

        eventInput = taskScheduler.buildInputWire("events with unvalidated signatures");
        minimumGenerationNonAncientInput = taskScheduler.buildInputWire("minimum generation non ancient");
    }

    /**
     * Get the input of the signature validator, i.e. a stream of events with unvalidated signatures.
     *
     * @return the event input wire
     */
    @NonNull
    public InputWire<GossipEvent, GossipEvent> getEventInput() {
        return eventInput;
    }

    /**
     * Get the input of the minimum generation non ancient
     *
     * @return the minimum generation non ancient input wire
     */
    @NonNull
    public InputWire<Long, GossipEvent> getMinimumGenerationNonAncientInput() {
        return minimumGenerationNonAncientInput;
    }

    /**
     * Get the output of the signature validator, i.e. a stream of events with valid signatures.
     *
     * @return the event output channel
     */
    @NonNull
    public OutputWire<GossipEvent> getEventOutput() {
        return taskScheduler.getOutputWire();
    }

    /**
     * Bind a signature validator to this wiring.
     *
     * @param eventSignatureValidator the event signature validator to bind
     */
    public void bind(@NonNull final EventSignatureValidator eventSignatureValidator) {
        eventInput.bind(eventSignatureValidator::validateSignature);
        minimumGenerationNonAncientInput.bind(eventSignatureValidator::setMinimumGenerationNonAncient);
    }
}
