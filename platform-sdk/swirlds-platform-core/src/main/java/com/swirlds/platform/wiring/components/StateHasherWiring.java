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

import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedStateHasher;
import com.swirlds.platform.wiring.StateAndRoundReserver;
import com.swirlds.platform.wiring.StateAndRoundToStateReserver;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for the {@link com.swirlds.platform.state.signed.SignedStateHasher}
 *
 * @param stateAndRoundInput  the input wire for the state to hash, with the corresponding round
 * @param stateAndRoundOutput the output wire for the hashed state, with the corresponding round
 * @param stateOutput         the output wire for the hashed state
 * @param roundOutput         the output wire for the consensus round
 * @param flushRunnable       the runnable to flush the task scheduler
 */
public record StateHasherWiring(
        @NonNull InputWire<StateAndRound> stateAndRoundInput,
        @NonNull OutputWire<StateAndRound> stateAndRoundOutput,
        @NonNull OutputWire<ReservedSignedState> stateOutput,
        @NonNull OutputWire<ConsensusRound> roundOutput,
        @NonNull Runnable flushRunnable) {

    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler for this wiring object
     * @return the new wiring instance
     */
    public static StateHasherWiring create(@NonNull final TaskScheduler<StateAndRound> taskScheduler) {
        final OutputWire<StateAndRound> stateAndRoundOutput = taskScheduler
                .getOutputWire()
                .buildAdvancedTransformer(new StateAndRoundReserver("postHasher_stateAndRoundReserver"));
        final OutputWire<ReservedSignedState> stateOutput = stateAndRoundOutput.buildAdvancedTransformer(
                new StateAndRoundToStateReserver("postHasher_stateReserver"));

        return new StateHasherWiring(
                taskScheduler.buildInputWire("state and round"),
                stateAndRoundOutput,
                stateOutput,
                taskScheduler
                        .getOutputWire()
                        .buildTransformer("postHasher_getConsensusRound", "stateAndRound", StateAndRound::round),
                taskScheduler::flush);
    }

    /**
     * Bind the given state hasher to this wiring.
     *
     * @param stateHasher the state hasher
     */
    public void bind(@NonNull final SignedStateHasher stateHasher) {
        ((BindableInputWire<StateAndRound, StateAndRound>) stateAndRoundInput).bind(stateHasher::hashState);
    }
}
