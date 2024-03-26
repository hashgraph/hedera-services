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

import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.transformers.WireTransformer;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.components.transaction.system.SystemTransactionExtractionUtils;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.StateSignatureCollector;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import com.swirlds.platform.wiring.ClearTrigger;
import com.swirlds.platform.wiring.SignedStateReserver;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;

/**
 * Wiring for the state signature collector.
 */
public class StateSignatureCollectorWiring {

    private final TaskScheduler<List<ReservedSignedState>> taskScheduler;
    private final BindableInputWire<List<ScopedSystemTransaction<StateSignatureTransaction>>, List<ReservedSignedState>>
            preConsSigInput;
    private final BindableInputWire<List<ScopedSystemTransaction<StateSignatureTransaction>>, List<ReservedSignedState>>
            postConsSigInput;
    private final BindableInputWire<ReservedSignedState, List<ReservedSignedState>> reservedStateInput;
    private final BindableInputWire<ClearTrigger, List<ReservedSignedState>> clearInput;
    private final InputWire<GossipEvent> preConsensusEventInput;
    private final InputWire<ConsensusRound> postConsensusEventInput;
    private final OutputWire<ReservedSignedState> allStatesOutput;
    private final OutputWire<ReservedSignedState> completeStatesOutput;

    /**
     * Constructor.
     *
     * @param model         the wiring model for the platform
     * @param taskScheduler the task scheduler that will perform the prehandling
     */
    private StateSignatureCollectorWiring(
            @NonNull final WiringModel model, @NonNull final TaskScheduler<List<ReservedSignedState>> taskScheduler) {

        this.taskScheduler = Objects.requireNonNull(taskScheduler);
        // Create the outputs
        final OutputWire<ReservedSignedState> stateSplitter =
                taskScheduler.getOutputWire().buildSplitter("reservedStateSplitter", "reserved state lists");
        this.allStatesOutput = stateSplitter.buildAdvancedTransformer(new SignedStateReserver("allStatesReserver"));
        this.completeStatesOutput = allStatesOutput
                .buildFilter("completeStateFilter", "states", StateSignatureCollectorWiring::completeStates)
                .buildAdvancedTransformer(new SignedStateReserver("completeStatesReserver"));

        // Create input for preconsensus signatures
        final WireTransformer<GossipEvent, List<ScopedSystemTransaction<StateSignatureTransaction>>>
                preConsensusTransformer = new WireTransformer<>(
                        model,
                        "extractPreconsensusSignatureTransactions",
                        "preconsensus events",
                        event -> SystemTransactionExtractionUtils.extractFromEvent(
                                event, StateSignatureTransaction.class));
        preConsensusEventInput = preConsensusTransformer.getInputWire();
        preConsSigInput = taskScheduler.buildInputWire("preconsensus signature transactions");
        preConsensusTransformer.getOutputWire().solderTo(preConsSigInput);

        // Create input for consensus signatures
        final WireTransformer<ConsensusRound, List<ScopedSystemTransaction<StateSignatureTransaction>>>
                postConsensusTransformer = new WireTransformer<>(
                        model,
                        "extractConsensusSignatureTransactions",
                        "consensus events",
                        round -> SystemTransactionExtractionUtils.extractFromRound(
                                round, StateSignatureTransaction.class));
        postConsensusEventInput = postConsensusTransformer.getInputWire();
        postConsSigInput = taskScheduler.buildInputWire("consensus signature transactions");
        postConsensusTransformer.getOutputWire().solderTo(postConsSigInput);

        // Create input for signed states
        reservedStateInput = taskScheduler.buildInputWire("state");

        // Create clear input
        clearInput = taskScheduler.buildInputWire("clear");
    }

    private static boolean completeStates(@NonNull final ReservedSignedState rs) {
        if (rs.get().isComplete()) {
            return true;
        }
        rs.close();
        return false;
    }

    /**
     * Create a new instance of this wiring.
     *
     * @param model         the wiring model
     * @param taskScheduler the task scheduler that will perform the prehandling
     * @return the new wiring instance
     */
    @NonNull
    public static StateSignatureCollectorWiring create(
            @NonNull final WiringModel model, @NonNull final TaskScheduler<List<ReservedSignedState>> taskScheduler) {
        return new StateSignatureCollectorWiring(model, taskScheduler);
    }

    /**
     * Bind the preconsensus event handler to the input wire.
     *
     * @param stateSignatureCollector collects and manages state signatures
     */
    public void bind(@NonNull final StateSignatureCollector stateSignatureCollector) {
        Objects.requireNonNull(stateSignatureCollector);
        preConsSigInput.bind(stateSignatureCollector::handlePreconsensusSignatures);
        postConsSigInput.bind(stateSignatureCollector::handlePostconsensusSignatures);
        reservedStateInput.bind(stateSignatureCollector::addReservedState);
        clearInput.bindConsumer(stateSignatureCollector::clear);
    }

    /** @return the input wire for the pre-consensus events (which contain signatures) */
    @NonNull
    public InputWire<GossipEvent> preConsensusEventInput() {
        return preConsensusEventInput;
    }

    /** @return the input wire for the states (that we need to collect signatures for) */
    @NonNull
    public InputWire<ReservedSignedState> getReservedStateInput() {
        return reservedStateInput;
    }

    /** @return the input wire for consensus rounds (which contain signatures) */
    @NonNull
    public InputWire<ConsensusRound> getConsensusRoundInput() {
        return postConsensusEventInput;
    }

    /** @return the output wire all states returned by the collector (complete, and old incomplete) */
    @NonNull
    public OutputWire<ReservedSignedState> getAllStatesOutput() {
        return allStatesOutput;
    }

    /** @return the output wire complete states returned by the collector */
    @NonNull
    public OutputWire<ReservedSignedState> getCompleteStatesOutput() {
        return completeStatesOutput;
    }

    /** @return the input wire that clears the collector */
    @NonNull
    public InputWire<ClearTrigger> getClearInput() {
        return clearInput;
    }

    /** Flush the task scheduler. */
    public void flush() {
        taskScheduler.flush();
    }
}
