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
import com.swirlds.platform.StateSigner;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The wiring for the {@link com.swirlds.platform.StateSigner}
 *
 * @param signState      the input wire for signing a state
 * @param stateSignature the output wire for the state signature
 */
public record StateSignerWiring(
        @NonNull InputWire<ReservedSignedState> signState,
        @NonNull OutputWire<StateSignatureTransaction> stateSignature) {

    /**
     * Create a new instance of this wiring
     *
     * @param scheduler the task scheduler for this wiring
     * @return the new wiring instance
     */
    public static StateSignerWiring create(@NonNull final TaskScheduler<StateSignatureTransaction> scheduler) {
        return new StateSignerWiring(scheduler.buildInputWire("state to sign"), scheduler.getOutputWire());
    }

    /**
     * Bind the wires to the {@link com.swirlds.platform.StateSigner}
     *
     * @param stateSigner the state signer
     */
    public void bind(@NonNull final StateSigner stateSigner) {
        ((BindableInputWire<ReservedSignedState, StateSignatureTransaction>) signState).bind(stateSigner::signState);
    }
}
