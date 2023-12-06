package com.swirlds.platform.wiring;

import com.swirlds.common.system.transaction.internal.StateSignatureTransaction;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.StateSigner;
import com.swirlds.platform.state.signed.ReservedSignedState;
import edu.umd.cs.findbugs.annotations.NonNull;

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
        return new StateSignerWiring(
                scheduler.buildInputWire("sign a state"),
                scheduler.getOutputWire());
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
