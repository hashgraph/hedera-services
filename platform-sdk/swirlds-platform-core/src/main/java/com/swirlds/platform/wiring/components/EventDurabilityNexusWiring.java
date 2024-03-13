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

package com.swirlds.platform.wiring.components;

import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.platform.event.preconsensus.EventDurabilityNexus;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for the {@link EventDurabilityNexus}.
 *
 * @param latestDurableSequenceNumber the input wire for the last sequence number durably flushed to disk
 */
public record EventDurabilityNexusWiring(@NonNull InputWire<Long> latestDurableSequenceNumber) {
    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler for this wiring
     * @return the new wiring instance
     */
    @NonNull
    public static EventDurabilityNexusWiring create(@NonNull final TaskScheduler<Void> taskScheduler) {
        return new EventDurabilityNexusWiring(taskScheduler.buildInputWire("latest durable sequence number"));
    }

    /**
     * Bind an event durability nexus to this wiring.
     *
     * @param nexus the event durability nexus to bind
     */
    public void bind(@NonNull final EventDurabilityNexus nexus) {
        ((BindableInputWire<Long, Void>) latestDurableSequenceNumber)
                .bindConsumer(nexus::setLatestDurableSequenceNumber);
    }
}
