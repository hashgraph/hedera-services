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

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.contract.impl.annotations.InitialState;
import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleSystemContractOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.SystemContractOperations;
import com.hedera.node.app.service.contract.impl.exec.utils.ActionStack;
import com.hedera.node.app.service.contract.impl.hevm.ActionSidecarContentTracer;
import com.hedera.node.app.service.contract.impl.hevm.HandleContextHevmBlocks;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmBlocks;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmContext;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.hevm.HydratedEthTxData;
import com.hedera.node.app.service.contract.impl.infra.EthereumCallDataHydration;
import com.hedera.node.app.service.contract.impl.state.EvmFrameStateFactory;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.ScopedEvmFrameStateFactory;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.function.Supplier;

@Module(includes = {TransactionConfigModule.class, TransactionInitialStateModule.class})
public interface TransactionModule {
    @Provides
    @TransactionScope
    static Instant provideConsensusTime(@NonNull final HandleContext context) {
        return requireNonNull(context).consensusNow();
    }

    @Provides
    @Nullable
    @TransactionScope
    static HydratedEthTxData maybeProvideHydratedEthTxData(
            @NonNull final HandleContext context,
            @NonNull final EthereumCallDataHydration hydration,
            @NonNull @InitialState final ReadableFileStore fileStore) {
        final var body = context.body();
        return body.hasEthereumTransaction()
                ? hydration.tryToHydrate(body.ethereumTransactionOrThrow(), fileStore)
                : null;
    }

    @Provides
    @TransactionScope
    static ActionSidecarContentTracer provideActionSidecarContentTracer() {
        return new EvmActionTracer(new ActionStack());
    }

    @Provides
    @TransactionScope
    static HederaEvmContext provideHederaEvmContext(
            @NonNull final HederaOperations extWorldScope, @NonNull final HederaEvmBlocks hederaEvmBlocks) {
        return new HederaEvmContext(extWorldScope.gasPriceInTinybars(), false, hederaEvmBlocks);
    }

    @Provides
    @TransactionScope
    static Supplier<HederaWorldUpdater> provideFeesOnlyUpdater(
            @NonNull final HederaOperations extWorldScope,
            @NonNull final SystemContractOperations systemContractOperations,
            @NonNull final EvmFrameStateFactory factory) {
        return () -> new ProxyWorldUpdater(
                requireNonNull(extWorldScope), requireNonNull(systemContractOperations), requireNonNull(factory), null);
    }

    @Provides
    @TransactionScope
    static AttributeValidator provideAttributeValidator(@NonNull final HandleContext context) {
        return context.attributeValidator();
    }

    @Provides
    @TransactionScope
    static ExpiryValidator provideExpiryValidator(@NonNull final HandleContext context) {
        return context.expiryValidator();
    }

    @Provides
    @TransactionScope
    static NetworkInfo provideNetworkInfo(@NonNull final HandleContext context) {
        return context.networkInfo();
    }

    @Binds
    @TransactionScope
    EvmFrameStateFactory bindEvmFrameStateFactory(ScopedEvmFrameStateFactory factory);

    @Binds
    @TransactionScope
    HederaOperations bindExtWorldScope(HandleHederaOperations handleExtWorldScope);

    @Binds
    @TransactionScope
    HederaNativeOperations bindExtFrameScope(HandleHederaNativeOperations handleExtFrameScope);

    @Binds
    @TransactionScope
    SystemContractOperations bindExtSystemContractScope(HandleSystemContractOperations handleSystemContractOperations);

    @Binds
    @TransactionScope
    HederaEvmBlocks bindHederaEvmBlocks(HandleContextHevmBlocks handleContextHevmBlocks);
}
