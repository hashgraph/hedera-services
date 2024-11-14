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

import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.spi.AppContext.Gossip.UNAVAILABLE_GOSSIP;
import static com.hedera.node.app.workflows.standalone.impl.NoopVerificationStrategies.NOOP_VERIFICATION_STRATEGIES;

import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.info.NodeInfoImpl;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.services.AppContextImpl;
import com.hedera.node.app.signature.AppSignatureVerifier;
import com.hedera.node.app.signature.impl.SignatureExpanderImpl;
import com.hedera.node.app.signature.impl.SignatureVerifierImpl;
import com.hedera.node.app.state.recordcache.LegacyListRecordSource;
import com.hedera.node.app.tss.TssBaseServiceImpl;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;
import org.hyperledger.besu.evm.tracing.OperationTracer;

/**
 * A factory for creating {@link TransactionExecutor} instances.
 */
public enum TransactionExecutors {
    TRANSACTION_EXECUTORS;
    public static final NodeInfo DEFAULT_NODE_INFO = new NodeInfoImpl(0, asAccount(3L), 10, List.of(), Bytes.EMPTY);

    /**
     * A strategy to bind and retrieve {@link OperationTracer} scoped to a thread.
     */
    public interface TracerBinding extends Supplier<List<OperationTracer>> {
        void runWhere(@NonNull List<OperationTracer> tracers, @NonNull Runnable runnable);
    }

    /**
     * Creates a new {@link TransactionExecutor} based on the given {@link State} and properties.
     *
     * @param state the {@link State} to create the executor from
     * @param properties the properties to use for the executor
     * @param customTracerBinding if not null, the tracer binding to use
     * @return a new {@link TransactionExecutor}
     */
    public TransactionExecutor newExecutor(
            @NonNull final State state,
            @NonNull final Map<String, String> properties,
            @Nullable final TracerBinding customTracerBinding) {
        final var tracerBinding =
                customTracerBinding != null ? customTracerBinding : DefaultTracerBinding.DEFAULT_TRACER_BINDING;
        final var executor = newExecutorComponent(properties, tracerBinding);
        executor.initializer().accept(state);
        executor.stateNetworkInfo().initFrom(state);
        final var exchangeRateManager = executor.exchangeRateManager();
        return (transactionBody, consensusNow, operationTracers) -> {
            final var dispatch = executor.standaloneDispatchFactory().newDispatch(state, transactionBody, consensusNow);
            tracerBinding.runWhere(List.of(operationTracers), () -> executor.dispatchProcessor()
                    .processDispatch(dispatch));
            final var recordSource = dispatch.stack()
                    .buildHandleOutput(consensusNow, exchangeRateManager.exchangeRates())
                    .recordSourceOrThrow();
            return ((LegacyListRecordSource) recordSource).precomputedRecords();
        };
    }

    private ExecutorComponent newExecutorComponent(
            @NonNull final Map<String, String> properties, @NonNull final TracerBinding tracerBinding) {
        final var bootstrapConfigProvider = new BootstrapConfigProviderImpl();
        final var appContext = new AppContextImpl(
                InstantSource.system(),
                new AppSignatureVerifier(
                        bootstrapConfigProvider.getConfiguration().getConfigData(HederaConfig.class),
                        new SignatureExpanderImpl(),
                        new SignatureVerifierImpl(CryptographyHolder.get())),
                UNAVAILABLE_GOSSIP,
                bootstrapConfigProvider::getConfiguration,
                () -> DEFAULT_NODE_INFO);
        final var tssBaseService = new TssBaseServiceImpl(
                appContext,
                ForkJoinPool.commonPool(),
                ForkJoinPool.commonPool(),
                new PlaceholderTssLibrary(),
                ForkJoinPool.commonPool(),
                new NoOpMetrics());
        final var contractService = new ContractServiceImpl(appContext, NOOP_VERIFICATION_STRATEGIES, tracerBinding);
        final var fileService = new FileServiceImpl();
        final var configProvider = new ConfigProviderImpl(false, null, properties);
        return DaggerExecutorComponent.builder()
                .configProviderImpl(configProvider)
                .bootstrapConfigProviderImpl(bootstrapConfigProvider)
                .tssBaseService(tssBaseService)
                .fileServiceImpl(fileService)
                .contractServiceImpl(contractService)
                .metrics(new NoOpMetrics())
                .build();
    }

    /**
     * The default {@link TracerBinding} implementation that uses a {@link ThreadLocal}.
     */
    private enum DefaultTracerBinding implements TracerBinding {
        DEFAULT_TRACER_BINDING;

        private static final ThreadLocal<List<OperationTracer>> OPERATION_TRACERS = ThreadLocal.withInitial(List::of);

        @Override
        public void runWhere(@NonNull final List<OperationTracer> tracers, @NonNull final Runnable runnable) {
            OPERATION_TRACERS.set(tracers);
            runnable.run();
        }

        @Override
        public List<OperationTracer> get() {
            return OPERATION_TRACERS.get();
        }
    }
}
