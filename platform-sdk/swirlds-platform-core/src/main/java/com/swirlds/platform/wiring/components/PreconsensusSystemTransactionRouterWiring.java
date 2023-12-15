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

import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.components.transaction.system.SystemTransactionExtractor;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import com.swirlds.platform.system.transaction.SystemTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Wiring for routing of system transactions.
 */
public record PreconsensusSystemTransactionRouterWiring(
        @NonNull InputWire<GossipEvent> preconsensusEventInout,
        @NonNull OutputWire<StateSignatureTransaction> stateSignatureTransactionOutput) {

    public static PreconsensusSystemTransactionRouterWiring create(
            @NonNull final TaskScheduler<List<SystemTransaction>> taskScheduler) {

        final BindableInputWire<GossipEvent, List<SystemTransaction>> preconsensusEventInput =
                taskScheduler.buildInputWire("preconsensus events");

        // Since this is stateless static code, we can bind it to the input wire immediately.
        preconsensusEventInput.bind(SystemTransactionExtractor::getSystemTransactions);

        final OutputWire<List<SystemTransaction>> systemTransactions = taskScheduler.getOutputWire();
        final OutputWire<SystemTransaction> individualSystemTransactions = systemTransactions.buildSplitter();
        final OutputWire<StateSignatureTransaction> stateSignatureTransactionOutput =
                individualSystemTransactions.buildTransformer(
                        "signature transactions filter", SystemTransactionExtractor::stateSignatureTransactionFilter);

        return new PreconsensusSystemTransactionRouterWiring(preconsensusEventInput, stateSignatureTransactionOutput);
    }
}
