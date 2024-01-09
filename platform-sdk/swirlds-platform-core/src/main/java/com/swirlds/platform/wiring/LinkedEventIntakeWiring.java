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

package com.swirlds.platform.wiring;

import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.components.LinkedEventIntake;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Wiring for the {@link LinkedEventIntake}.
 *
 * @param eventInput                        the input wire for events to be added to the hashgraph
 * @param pauseInput                        the input wire for pausing the linked event intake
 * @param consensusRoundOutput              the output wire for consensus rounds
 * @param nonAncientEventWindowOutput       the output wire for the {@link NonAncientEventWindow}. This output is
 *                                          transformed from the consensus round output
 * @param minimumGenerationNonAncientOutput the output wire for the minimum generation non-ancient. This output is
 *                                          transformed from the consensus round output
 * @param flushRunnable                     the runnable to flush the intake
 */
public record LinkedEventIntakeWiring(
        @NonNull InputWire<EventImpl> eventInput,
        @NonNull InputWire<Boolean> pauseInput,
        @NonNull OutputWire<ConsensusRound> consensusRoundOutput,
        @NonNull OutputWire<NonAncientEventWindow> nonAncientEventWindowOutput,
        @NonNull OutputWire<Long> minimumGenerationNonAncientOutput,
        @NonNull Runnable flushRunnable) {

    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler for this intake
     * @return the new wiring instance
     */
    public static LinkedEventIntakeWiring create(@NonNull final TaskScheduler<List<ConsensusRound>> taskScheduler) {
        final OutputWire<ConsensusRound> consensusRoundOutput =
                taskScheduler.getOutputWire().buildSplitter("linkedEventIntakeSplitter", "round lists");

        return new LinkedEventIntakeWiring(
                taskScheduler.buildInputWire("linked events"),
                taskScheduler.buildInputWire("pause"),
                consensusRoundOutput,
                consensusRoundOutput.buildTransformer(
                        "getNonAncientEventWindow",
                        "rounds",
                        consensusRound -> consensusRound.getNonAncientEventWindow()),
                consensusRoundOutput.buildTransformer(
                        "getMinimumGenerationNonAncient",
                        "rounds",
                        consensusRound -> consensusRound.getGenerations().getMinGenerationNonAncient()),
                taskScheduler::flush);
    }

    /**
     * Bind a linked event intake object to this scheduler.
     *
     * @param linkedEventIntake the linked event intake to bind
     */
    public void bind(@NonNull final LinkedEventIntake linkedEventIntake) {
        ((BindableInputWire<EventImpl, List<ConsensusRound>>) eventInput).bind(linkedEventIntake::addEvent);
        ((BindableInputWire<Boolean, List<GossipEvent>>) pauseInput).bind(linkedEventIntake::setPaused);
    }
}
