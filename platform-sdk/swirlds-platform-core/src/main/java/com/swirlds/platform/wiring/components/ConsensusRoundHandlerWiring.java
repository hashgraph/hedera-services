/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import com.swirlds.common.stream.RunningEventHashUpdate;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.wiring.StateAndRoundReserver;
import com.swirlds.platform.wiring.StateAndRoundToStateReserver;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for the {@link com.swirlds.platform.eventhandling.ConsensusRoundHandler}
 *
 * @param roundInput              the input wire for consensus rounds to be applied to the state
 * @param runningHashUpdateInput  the input wire for updating the running event hash
 * @param stateAndRoundOutput     the output wire for the reserved signed state, bundled with the round that caused
 *                                the state to be created
 * @param stateOutput             the output wire for the reserved signed state
 * @param roundNumberOutput       the output wire for the round number
 * @param flushRunnable           the runnable to flush the task scheduler
 * @param startSquelchingRunnable the runnable to start squelching
 * @param stopSquelchingRunnable  the runnable to stop squelching
 */
public record ConsensusRoundHandlerWiring(
        @NonNull InputWire<ConsensusRound> roundInput,
        @NonNull InputWire<RunningEventHashUpdate> runningHashUpdateInput,
        @NonNull OutputWire<StateAndRound> stateAndRoundOutput,
        @NonNull OutputWire<ReservedSignedState> stateOutput,
        @NonNull OutputWire<Long> roundNumberOutput,
        @NonNull Runnable flushRunnable,
        @NonNull Runnable startSquelchingRunnable,
        @NonNull Runnable stopSquelchingRunnable) {
    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler for this wiring object
     * @return the new wiring instance
     */
    @NonNull
    public static ConsensusRoundHandlerWiring create(@NonNull final TaskScheduler<StateAndRound> taskScheduler) {
        final OutputWire<StateAndRound> stateAndRoundOutput = taskScheduler
                .getOutputWire()
                .buildAdvancedTransformer(new StateAndRoundReserver("postHandler_stateAndRoundReserver"));
        final OutputWire<ReservedSignedState> stateOutput = stateAndRoundOutput.buildAdvancedTransformer(
                new StateAndRoundToStateReserver("postHandler_stateReserver"));

        return new ConsensusRoundHandlerWiring(
                taskScheduler.buildInputWire("rounds"),
                taskScheduler.buildInputWire("running hash update"),
                stateAndRoundOutput,
                stateOutput,
                taskScheduler
                        .getOutputWire()
                        .buildTransformer("postHandler_getRoundNumber", "stateAndRound", stateAndRound -> stateAndRound
                                .round()
                                .getRoundNum()),
                taskScheduler::flush,
                taskScheduler::startSquelching,
                taskScheduler::stopSquelching);
    }

    /**
     * Bind the consensus round handler to this wiring.
     *
     * @param consensusRoundHandler the consensus round handler to bind
     */
    public void bind(@NonNull final ConsensusRoundHandler consensusRoundHandler) {
        ((BindableInputWire<ConsensusRound, StateAndRound>) roundInput)
                .bind(consensusRoundHandler::handleConsensusRound);
        ((BindableInputWire<RunningEventHashUpdate, StateAndRound>) runningHashUpdateInput)
                .bindConsumer(consensusRoundHandler::updateRunningHash);
    }
}
