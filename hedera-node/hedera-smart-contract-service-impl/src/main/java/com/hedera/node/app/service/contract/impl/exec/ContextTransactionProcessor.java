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

import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.hevm.ActionSidecarContentTracer;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmContext;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransaction;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmVersion;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.hevm.HydratedEthTxData;
import com.hedera.node.app.service.contract.impl.infra.HevmTransactionFactory;
import com.hedera.node.app.service.contract.impl.state.RootProxyWorldUpdater;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.data.ContractsConfig;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import javax.inject.Inject;

/**
 * A small utility that runs the
 * {@link TransactionProcessor#processTransaction(HederaEvmTransaction, HederaWorldUpdater, Supplier, HederaEvmContext, ActionSidecarContentTracer, Configuration)}
 * call implied by the in-scope {@link HandleContext}.
 */
@TransactionScope
public class ContextTransactionProcessor implements Callable<CallOutcome> {
    private final HandleContext context;
    private final ContractsConfig contractsConfig;
    private final Configuration configuration;
    private final HederaEvmContext hederaEvmContext;

    @Nullable
    private final HydratedEthTxData hydratedEthTxData;

    private final ActionSidecarContentTracer tracer;
    private final RootProxyWorldUpdater rootProxyWorldUpdater;
    private final HevmTransactionFactory hevmTransactionFactory;
    private final Supplier<HederaWorldUpdater> feesOnlyUpdater;
    private final Map<HederaEvmVersion, TransactionProcessor> processors;

    @Inject
    public ContextTransactionProcessor(
            @Nullable final HydratedEthTxData hydratedEthTxData,
            @NonNull final HandleContext context,
            @NonNull final ContractsConfig contractsConfig,
            @NonNull final Configuration configuration,
            @NonNull final HederaEvmContext hederaEvmContext,
            @NonNull final ActionSidecarContentTracer tracer,
            @NonNull final RootProxyWorldUpdater worldUpdater,
            @NonNull final HevmTransactionFactory hevmTransactionFactory,
            @NonNull final Supplier<HederaWorldUpdater> feesOnlyUpdater,
            @NonNull final Map<HederaEvmVersion, TransactionProcessor> processors) {
        this.context = Objects.requireNonNull(context);
        this.hydratedEthTxData = hydratedEthTxData;
        this.tracer = Objects.requireNonNull(tracer);
        this.feesOnlyUpdater = Objects.requireNonNull(feesOnlyUpdater);
        this.processors = Objects.requireNonNull(processors);
        this.rootProxyWorldUpdater = Objects.requireNonNull(worldUpdater);
        this.configuration = Objects.requireNonNull(configuration);
        this.contractsConfig = Objects.requireNonNull(contractsConfig);
        this.hederaEvmContext = Objects.requireNonNull(hederaEvmContext);
        this.hevmTransactionFactory = Objects.requireNonNull(hevmTransactionFactory);
    }

    @Override
    public CallOutcome call() {
        // Ensure that if this is an EthereumTransaction, we have a valid EthTxData
        assertEthTxDataValidIfApplicable();

        // Try to translate the HAPI operation to a Hedera EVM transaction, throw HandleException on failure
        final var hevmTransaction = hevmTransactionFactory.fromHapiTransaction(context.body());

        // Get the appropriate processor for the EVM version
        final var processor = processors.get(EVM_VERSIONS.get(contractsConfig.evmVersion()));

        // Process the transaction
        final var result = processor.processTransaction(
                hevmTransaction, rootProxyWorldUpdater, feesOnlyUpdater, hederaEvmContext, tracer, configuration);

        // Return the outcome, maybe enriched with details of the base commit and Ethereum transaction
        return new CallOutcome(
                result.asProtoResultOf(ethTxDataIfApplicable(), rootProxyWorldUpdater), result.finalStatus());
    }

    private void assertEthTxDataValidIfApplicable() {
        if (hydratedEthTxData != null && !hydratedEthTxData.isAvailable()) {
            throw new HandleException(hydratedEthTxData.status());
        }
    }

    private @Nullable EthTxData ethTxDataIfApplicable() {
        return hydratedEthTxData == null ? null : hydratedEthTxData.ethTxData();
    }
}
