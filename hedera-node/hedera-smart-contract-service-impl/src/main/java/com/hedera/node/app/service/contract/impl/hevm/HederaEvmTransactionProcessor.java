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

package com.hedera.node.app.service.contract.impl.hevm;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.contract.impl.exec.TransactionProcessor;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.hyperledger.besu.evm.tracing.OperationTracer;

@Singleton
public class HederaEvmTransactionProcessor {
    private final Map<HederaEvmVersion, TransactionProcessor> transactionProcessors;

    @Inject
    public HederaEvmTransactionProcessor(
            @NonNull final Map<HederaEvmVersion, TransactionProcessor> transactionProcessors) {
        this.transactionProcessors = requireNonNull(transactionProcessors);
    }

    public HederaEvmTransactionResult process(
            @NonNull final HederaEvmTransaction transaction,
            @NonNull final HederaWorldUpdater worldUpdater,
            @NonNull final HederaEvmContext context,
            @NonNull final HederaEvmVersion version,
            @NonNull final OperationTracer tracer,
            @NonNull final Configuration config) {
        return requireNonNull(transactionProcessors.get(version))
                .processTransaction(
                        requireNonNull(transaction),
                        requireNonNull(worldUpdater),
                        requireNonNull(context),
                        requireNonNull(tracer),
                        requireNonNull(config));
    }
}
