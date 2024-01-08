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
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.preconsensus.PcesSequencer;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The wiring for the {@link PcesSequencer}.
 *
 * @param eventInput    the input wire for events to be sequenced
 * @param eventOutput   the output wire for sequenced events
 * @param flushRunnable the runnable to flush the sequencer
 */
public record PcesSequencerWiring(
        @NonNull InputWire<GossipEvent> eventInput,
        @NonNull OutputWire<GossipEvent> eventOutput,
        @NonNull Runnable flushRunnable) {

    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler for this wiring
     * @return the new wiring instance
     */
    @NonNull
    public static PcesSequencerWiring create(@NonNull final TaskScheduler<GossipEvent> taskScheduler) {
        return new PcesSequencerWiring(
                taskScheduler.buildInputWire("events to sequence"),
                taskScheduler.getOutputWire(),
                taskScheduler::flush);
    }

    /**
     * Bind an event sequencer to this wiring.
     *
     * @param sequencer the event sequencer to bind
     */
    public void bind(@NonNull final PcesSequencer sequencer) {
        ((BindableInputWire<GossipEvent, GossipEvent>) eventInput).bind(sequencer::assignStreamSequenceNumber);
    }
}
