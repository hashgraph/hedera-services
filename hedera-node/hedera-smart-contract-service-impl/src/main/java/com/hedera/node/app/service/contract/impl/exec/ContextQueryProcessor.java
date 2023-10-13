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

import static com.hedera.node.app.service.contract.impl.hevm.HederaEvmVersion.EVM_VERSIONS;

import com.hedera.node.app.service.contract.impl.annotations.QueryScope;
import com.hedera.node.app.service.contract.impl.hevm.ActionSidecarContentTracer;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmContext;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransaction;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmVersion;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.infra.HevmStaticTransactionFactory;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.config.data.ContractsConfig;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import javax.inject.Inject;

/**
 * A utility class for running
 * {@link TransactionProcessor#processTransaction(HederaEvmTransaction, HederaWorldUpdater, Supplier, HederaEvmContext, ActionSidecarContentTracer, Configuration)}
 * call implied by the in-scope {@link QueryContext}.
 * Analogous to {@link ContextTransactionProcessor} but for queries.
 */
@QueryScope
public class ContextQueryProcessor implements Callable<CallOutcome> {
    private final QueryContext context;
    private final HederaEvmContext hederaEvmContext;
    private final ActionSidecarContentTracer tracer;
    private final ProxyWorldUpdater worldUpdater;
    private final HevmStaticTransactionFactory hevmStaticTransactionFactory;
    private final Supplier<HederaWorldUpdater> feesOnlyUpdater;
    private final Map<HederaEvmVersion, TransactionProcessor> processors;

    @Inject
    public ContextQueryProcessor(
            @NonNull final QueryContext context,
            @NonNull final HederaEvmContext hederaEvmContext,
            @NonNull final ActionSidecarContentTracer tracer,
            @NonNull final ProxyWorldUpdater worldUpdater,
            @NonNull final HevmStaticTransactionFactory hevmStaticTransactionFactory,
            @NonNull final Supplier<HederaWorldUpdater> feesOnlyUpdater,
            @NonNull final Map<HederaEvmVersion, TransactionProcessor> processors) {
        this.context = Objects.requireNonNull(context);
        this.tracer = Objects.requireNonNull(tracer);
        this.feesOnlyUpdater = Objects.requireNonNull(feesOnlyUpdater);
        this.processors = Objects.requireNonNull(processors);
        this.worldUpdater = Objects.requireNonNull(worldUpdater);
        this.hederaEvmContext = Objects.requireNonNull(hederaEvmContext);
        this.hevmStaticTransactionFactory = Objects.requireNonNull(hevmStaticTransactionFactory);
    }

    @Override
    public CallOutcome call() {
        // Try to translate the HAPI operation to a Hedera EVM transaction, throw HandleException on failure
        final var hevmTransaction = hevmStaticTransactionFactory.fromHapiQuery(context.query());

        final var contractsConfig = context.configuration().getConfigData(ContractsConfig.class);
        // Get the appropriate processor for the EVM version
        final var processor = processors.get(EVM_VERSIONS.get(contractsConfig.evmVersion()));

        // Process the transaction
        final var result = processor.processTransaction(
                hevmTransaction, worldUpdater, feesOnlyUpdater, hederaEvmContext, tracer, context.configuration());

        // Return the outcome, maybe enriched with details of the base commit and Ethereum transaction
        return new CallOutcome(result.asQueryResult(), result.finalStatus());
    }
}
