/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
import com.swirlds.common.wiring.transformers.WireListSplitter;
import com.swirlds.common.wiring.transformers.WireTransformer;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.components.transaction.system.SystemTransactionExtractor;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.state.signed.SignedStateManager;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;

/**
 * Wiring for the state signature collector.
 */
public class StateSignatureCollectorWiring {

    private final TaskScheduler<Void> taskScheduler;
    private final BindableInputWire<ScopedSystemTransaction<StateSignatureTransaction>, Void>
            stateSignatureTransactionInput;
    private final InputWire<GossipEvent> preconsensusEventInput;

    /**
     * Constructor.
     *
     * @param model         the wiring model for the platform
     * @param taskScheduler the task scheduler that will perform the prehandling
     */
    private StateSignatureCollectorWiring(
            @NonNull final WiringModel model, @NonNull final TaskScheduler<Void> taskScheduler) {

        this.taskScheduler = Objects.requireNonNull(taskScheduler);

        final WireTransformer<GossipEvent, List<ScopedSystemTransaction<?>>> systemTransactionsFilter =
                new WireTransformer<>(
                        model,
                        "systemTransactionsFilter",
                        "preconsensus events",
                        SystemTransactionExtractor::getScopedSystemTransactions);
        preconsensusEventInput = systemTransactionsFilter.getInputWire();

        final WireListSplitter<ScopedSystemTransaction<?>> systemTransactionsSplitter =
                new WireListSplitter<>(model, "systemTransactionsSplitter", "system transaction lists");

        final WireTransformer<ScopedSystemTransaction<?>, ScopedSystemTransaction<StateSignatureTransaction>>
                stateSignatureTransactionFilter = new WireTransformer<>(
                        model,
                        "stateSignatureTransactionsFilter",
                        "system transactions",
                        SystemTransactionExtractor::stateSignatureTransactionFilter);

        stateSignatureTransactionInput = taskScheduler.buildInputWire("state signature transactions");

        systemTransactionsFilter.getOutputWire().solderTo(systemTransactionsSplitter.getInputWire());
        systemTransactionsSplitter.getOutputWire().solderTo(stateSignatureTransactionFilter.getInputWire());
        stateSignatureTransactionFilter.getOutputWire().solderTo(stateSignatureTransactionInput);
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
            @NonNull final WiringModel model, @NonNull final TaskScheduler<Void> taskScheduler) {
        return new StateSignatureCollectorWiring(model, taskScheduler);
    }

    /**
     * Bind the preconsensus event handler to the input wire.
     *
     * @param signedStateManager collects and manages state signatures
     */
    public void bind(@NonNull final SignedStateManager signedStateManager) {
        Objects.requireNonNull(signedStateManager);

        stateSignatureTransactionInput.bind(scopedTransaction -> {
            signedStateManager.handlePostconsensusSignatureTransaction(
                    scopedTransaction.submitterId(), scopedTransaction.transaction());
        });
    }

    /**
     * Get the input wire for the preconsensus events.
     *
     * @return the input wire
     */
    @NonNull
    public InputWire<GossipEvent> preconsensusEventInput() {
        return preconsensusEventInput;
    }

    /**
     * Flush the task scheduler.
     */
    public void flush() {
        taskScheduler.flush();
    }
}
