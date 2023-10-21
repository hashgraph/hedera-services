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

import com.swirlds.common.wiring.InputChannel;
import com.swirlds.common.wiring.OutputChannel;
import com.swirlds.common.wiring.Wire;
import com.swirlds.common.wiring.WiringModel;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.validation.EventValidator;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for the event signature validator.
 */
public class EventSignatureValidationWire {

    private final Wire<GossipEvent> wire;

    private final InputChannel<GossipEvent, GossipEvent> eventInput;
    private final InputChannel<Long, GossipEvent> minimumGenerationNonAncientInput;

    /**
     * Constructor.
     *
     * @param model the wiring model
     */
    public EventSignatureValidationWire(@NonNull final WiringModel model) {
        wire = model.wireBuilder("eventSignatureValidator")
                .withConcurrency(false)
                .withUnhandledTaskCapacity(500)
                .withFlushingEnabled(true)
                .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                .build()
                .cast();

        eventInput = wire.buildInputChannel("unvalidated events");
        minimumGenerationNonAncientInput = wire.buildInputChannel("minimum generation non ancient");
    }

    /**
     * Passes events to the signature validator.
     *
     * @return the event input channel
     */
    @NonNull
    public InputChannel<GossipEvent, GossipEvent> getEventInput() {
        return eventInput;
    }

    /**
     * Passes the minimum generation non ancient to the signature validator.
     *
     * @return the minimum generation non ancient input channel
     */
    @NonNull
    public InputChannel<Long, GossipEvent> getMinimumGenerationNonAncientInput() {
        return minimumGenerationNonAncientInput;
    }

    /**
     * Get the output of the signature validator, i.e. a stream of events with valid signatures.
     *
     * @return the event output channel
     */
    @NonNull
    public OutputChannel<GossipEvent> getEventOutput() {
        return wire;
    }

    /**
     * Bind an orphan buffer to this wiring.
     *
     * @param eventValidator the orphan buffer to bind
     */
    public void bind(@NonNull final EventValidator eventValidator) {
        // Future work:
        //   - ensure that the signature validator passed in is the new implementation.
        //   - Bind the input channels to the appropriate functions.
        //   - Ensure that functions return a value instead of passing it to an internal lambda.
    }
}
