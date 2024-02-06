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
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.internal.ConsensusRound;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for the {@link com.swirlds.platform.eventhandling.ConsensusRoundHandler}
 *
 * @param roundInput             the input wire for consensus rounds to be applied to the state
 * @param runningHashUpdateInput the input wire for updating the running event hash
 * @param flushRunnable          the runnable to flush the task scheduler
 */
public record ConsensusRoundHandlerWiring(
        @NonNull InputWire<ConsensusRound> roundInput,
        @NonNull InputWire<RunningEventHashUpdate> runningHashUpdateInput,
        @NonNull Runnable flushRunnable) {
    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler for this wiring object
     * @return the new wiring instance
     */
    @NonNull
    public static ConsensusRoundHandlerWiring create(@NonNull final TaskScheduler<Void> taskScheduler) {
        return new ConsensusRoundHandlerWiring(
                taskScheduler.buildInputWire("rounds"),
                taskScheduler.buildInputWire("running hash update"),
                taskScheduler::flush);
    }

    /**
     * Bind the consensus round handler to this wiring.
     *
     * @param consensusRoundHandler the consensus round handler to bind
     */
    public void bind(@NonNull final ConsensusRoundHandler consensusRoundHandler) {
        ((BindableInputWire<ConsensusRound, Void>) roundInput).bind(consensusRoundHandler::handleConsensusRound);
        ((BindableInputWire<RunningEventHashUpdate, Void>) runningHashUpdateInput)
                .bind(consensusRoundHandler::updateRunningHash);
    }
}
