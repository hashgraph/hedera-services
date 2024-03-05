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
import com.swirlds.platform.components.ConsensusEngine;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Wiring for the {@link ConsensusEngine}.
 *
 * @param eventInput              the input wire for events to be added to the hashgraph
 * @param consensusRoundOutput    the output wire for consensus rounds
 * @param consensusEventsOutput   the output wire for consensus events, transformed from the consensus round
 *                                output
 * @param flushRunnable           the runnable to flush the intake
 * @param startSquelchingRunnable the runnable to start squelching
 * @param stopSquelchingRunnable  the runnable to stop squelching
 */
public record ConsensusEngineWiring(
        @NonNull InputWire<EventImpl> eventInput,
        @NonNull OutputWire<ConsensusRound> consensusRoundOutput,
        @NonNull OutputWire<List<EventImpl>> consensusEventsOutput,
        @NonNull Runnable flushRunnable,
        @NonNull Runnable startSquelchingRunnable,
        @NonNull Runnable stopSquelchingRunnable) {

    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler for this wiring
     * @return the new wiring instance
     */
    public static ConsensusEngineWiring create(@NonNull final TaskScheduler<List<ConsensusRound>> taskScheduler) {
        final OutputWire<ConsensusRound> consensusRoundOutput =
                taskScheduler.getOutputWire().buildSplitter("consensusEngineSplitter", "round lists");

        return new ConsensusEngineWiring(
                taskScheduler.buildInputWire("linked events"),
                consensusRoundOutput,
                consensusRoundOutput.buildTransformer("getEvents", "rounds", ConsensusRound::getConsensusEvents),
                taskScheduler::flush,
                taskScheduler::startSquelching,
                taskScheduler::stopSquelching);
    }

    /**
     * Bind a consensus engine object to this scheduler.
     *
     * @param consensusEngine the consensus engine to bind
     */
    public void bind(@NonNull final ConsensusEngine consensusEngine) {
        ((BindableInputWire<EventImpl, List<ConsensusRound>>) eventInput).bind(consensusEngine::addEvent);
    }
}
