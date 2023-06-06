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

package com.hedera.node.app.service.contract.impl.exec;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

public class TransactionProcessor {
    public static final String CONFIG_CONTEXT_VARIABLE = "contractsConfig";

    private final GasCalculator gasCalculator;
    private final MessageCallProcessor messageCallProcessor;
    private final ContractCreationProcessor contractCreationProcessor;

    public TransactionProcessor(
            @NonNull final GasCalculator gasCalculator,
            @NonNull final MessageCallProcessor messageCallProcessor,
            @NonNull final ContractCreationProcessor contractCreationProcessor) {
        this.gasCalculator = Objects.requireNonNull(gasCalculator);
        this.messageCallProcessor = Objects.requireNonNull(messageCallProcessor);
        this.contractCreationProcessor = Objects.requireNonNull(contractCreationProcessor);
    }

    public void processTransaction(@NonNull final WorldUpdater worldUpdater) {
        throw new AssertionError("Not implemented");
    }
}
