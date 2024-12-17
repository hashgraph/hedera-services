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

import static com.swirlds.common.wiring.model.diagram.HyperlinkBuilder.platformCoreHyperlink;
import static com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType.DIRECT;

import com.swirlds.common.io.IOIterator;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.common.wiring.wires.output.StandardOutputWire;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.event.preconsensus.PcesReplayer;
import com.swirlds.platform.wiring.NoInput;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The wiring for the {@link PcesReplayer}.
 *
 * @param pcesIteratorInputWire       the input wire for the iterator of events to replay
 * @param doneStreamingPcesOutputWire the output wire which indicates that PCES replay is complete
 * @param eventOutput                 the secondary output wire, for events to be passed into the intake pipeline during
 *                                    replay
 */
public record PcesReplayerWiring(
        @NonNull InputWire<IOIterator<PlatformEvent>> pcesIteratorInputWire,
        @NonNull OutputWire<NoInput> doneStreamingPcesOutputWire,
        @NonNull StandardOutputWire<PlatformEvent> eventOutput) {

    /**
     * Create a new instance of this wiring.
     *
     * @param model the wiring model
     * @return the new wiring instance
     */
    @NonNull
    public static PcesReplayerWiring create(@NonNull final WiringModel model) {
        final TaskScheduler<NoInput> taskScheduler = model.schedulerBuilder("pcesReplayer")
                .withType(DIRECT)
                .withHyperlink(platformCoreHyperlink(PcesReplayer.class))
                .build()
                .cast();

        return new PcesReplayerWiring(
                taskScheduler.buildInputWire("event files to replay"),
                taskScheduler.getOutputWire(),
                taskScheduler.buildSecondaryOutputWire());
    }

    /**
     * Bind the given {@link PcesReplayer} to this wiring.
     *
     * @param pcesReplayer the replayer to bind
     */
    public void bind(@NonNull final PcesReplayer pcesReplayer) {
        ((BindableInputWire<IOIterator<PlatformEvent>, NoInput>) pcesIteratorInputWire).bind(pcesReplayer::replayPces);
    }
}
