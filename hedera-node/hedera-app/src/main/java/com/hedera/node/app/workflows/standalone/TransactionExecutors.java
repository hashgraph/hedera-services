/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.standalone;

import static com.hedera.node.app.workflows.standalone.impl.NoopVerificationStrategies.NOOP_VERIFICATION_STRATEGIES;

import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.services.AppContextImpl;
import com.hedera.node.app.signature.AppSignatureVerifier;
import com.hedera.node.app.signature.impl.SignatureExpanderImpl;
import com.hedera.node.app.signature.impl.SignatureVerifierImpl;
import com.hedera.node.config.data.HederaConfig;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import org.hyperledger.besu.evm.tracing.OperationTracer;

/**
 * A factory for creating {@link TransactionExecutor} instances.
 */
public enum TransactionExecutors {
    TRANSACTION_EXECUTORS;

    private static final ThreadLocal<List<OperationTracer>> OPERATION_TRACERS = ThreadLocal.withInitial(List::of);

    /**
     * Creates a new {@link TransactionExecutor} based on the given {@link State} and properties.
     * @param state the {@link State} to create the executor from
     * @param properties the properties to use for the executor
     * @return a new {@link TransactionExecutor}
     */
    public TransactionExecutor newExecutor(@NonNull final State state, @NonNull final Map<String, String> properties) {
        final var executor = newExecutorComponent(properties);
        executor.initializer().accept(state);
        executor.stateNetworkInfo().initFrom(state);
        return (transactionBody, consensusNow, operationTracers) -> {
            final var dispatch = executor.standaloneDispatchFactory().newDispatch(state, transactionBody, consensusNow);
            OPERATION_TRACERS.set(List.of(operationTracers));
            executor.dispatchProcessor().processDispatch(dispatch);
            return dispatch.stack().buildHandleOutput(consensusNow).recordsOrThrow();
        };
    }

    private ExecutorComponent newExecutorComponent(@NonNull final Map<String, String> properties) {
        final var bootstrapConfigProvider = new BootstrapConfigProviderImpl();
        final var appContext = new AppContextImpl(
                InstantSource.system(),
                new AppSignatureVerifier(
                        bootstrapConfigProvider.getConfiguration().getConfigData(HederaConfig.class),
                        new SignatureExpanderImpl(),
                        new SignatureVerifierImpl(CryptographyHolder.get())));
        final var contractService =
                new ContractServiceImpl(appContext, NOOP_VERIFICATION_STRATEGIES, OPERATION_TRACERS::get);
        final var fileService = new FileServiceImpl();
        final var configProvider = new ConfigProviderImpl(false, null, properties);
        return DaggerExecutorComponent.builder()
                .configProviderImpl(configProvider)
                .bootstrapConfigProviderImpl(bootstrapConfigProvider)
                .fileServiceImpl(fileService)
                .contractServiceImpl(contractService)
                .metrics(new NoOpMetrics())
                .build();
    }
}
