package com.swirlds.platform.wiring.components;

import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.transformers.WireTransformer;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.components.transaction.system.SystemTransactionExtractor;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.state.iss.ConsensusHashManager;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import com.swirlds.platform.wiring.NoInput;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

public record IssDetectorWiring(
        @NonNull InputWire<NoInput> endOfPcesReplay,
        @NonNull InputWire<Long> roundCompletedInput,
        @NonNull InputWire<ConsensusRound> handleConsensusRound,
        @NonNull InputWire<List<ScopedSystemTransaction<StateSignatureTransaction>>> handlePostconsensusSignatures,
        @NonNull InputWire<ReservedSignedState> newStateHashed,
        @NonNull InputWire<ReservedSignedState> overridingState) {
    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler that will detect ISSs
     * @return the new wiring instance
     */
    @NonNull
    public static IssDetectorWiring create(@NonNull final WiringModel model, @NonNull final TaskScheduler<Void> taskScheduler) {
        final WireTransformer<ConsensusRound, List<ScopedSystemTransaction<StateSignatureTransaction>>>
                roundTransformer = new WireTransformer<>(
                model,
                "extractConsensusSignatureTransactions",
                "consensus round",
                new SystemTransactionExtractor<>(StateSignatureTransaction.class)::handleRound);
        final InputWire<List<ScopedSystemTransaction<StateSignatureTransaction>>> sigInput = taskScheduler.buildInputWire("handlePostconsensusSignatures");
        roundTransformer.getOutputWire().solderTo(sigInput);
        return new IssDetectorWiring(
                taskScheduler.buildInputWire("endOfPcesReplay"),
                taskScheduler.buildInputWire("roundCompleted"),
                roundTransformer.getInputWire(),
                sigInput,
                taskScheduler.buildInputWire("newStateHashed"),
                taskScheduler.buildInputWire("overridingState"));
    }

    public void bind(@NonNull final ConsensusHashManager hashManager) {
        ((BindableInputWire<NoInput, Void>) endOfPcesReplay).bind(hashManager::signalEndOfPreconsensusReplay);
        ((BindableInputWire<Long, Void>) roundCompletedInput).bind(hashManager::roundCompleted);
        ((BindableInputWire<List<ScopedSystemTransaction<StateSignatureTransaction>>, Void>) handlePostconsensusSignatures)
                .bind(hashManager::handlePostconsensusSignatures);
        ((BindableInputWire<ReservedSignedState, Void>) newStateHashed).bind(hashManager::newStateHashed);
        ((BindableInputWire<ReservedSignedState, Void>) overridingState).bind(hashManager::overridingState);
    }
}
