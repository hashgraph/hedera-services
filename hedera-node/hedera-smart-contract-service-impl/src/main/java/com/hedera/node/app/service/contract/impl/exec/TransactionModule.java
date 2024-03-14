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

package com.hedera.node.app.service.contract.impl.exec;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.node.app.service.contract.impl.annotations.ChildTransactionResourcePrices;
import com.hedera.node.app.service.contract.impl.annotations.InitialState;
import com.hedera.node.app.service.contract.impl.annotations.TopLevelResourcePrices;
import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.exec.gas.CanonicalDispatchPrices;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.gas.TinybarValues;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleSystemContractOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.SystemContractOperations;
import com.hedera.node.app.service.contract.impl.exec.utils.ActionStack;
import com.hedera.node.app.service.contract.impl.exec.utils.PendingCreationMetadataRef;
import com.hedera.node.app.service.contract.impl.hevm.ActionSidecarContentTracer;
import com.hedera.node.app.service.contract.impl.hevm.HandleContextHevmBlocks;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmBlocks;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmContext;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.hevm.HydratedEthTxData;
import com.hedera.node.app.service.contract.impl.infra.EthereumCallDataHydration;
import com.hedera.node.app.service.contract.impl.records.ContractOperationRecordBuilder;
import com.hedera.node.app.service.contract.impl.state.EvmFrameStateFactory;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.ScopedEvmFrameStateFactory;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.ComputeDispatchFeesAsTopLevel;
import com.hedera.node.app.spi.workflows.FunctionalityResourcePrices;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.data.HederaConfig;
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
    static TinybarValues provideTinybarValues(
            @TopLevelResourcePrices @NonNull final FunctionalityResourcePrices topLevelResourcePrices,
            @ChildTransactionResourcePrices @NonNull final FunctionalityResourcePrices childTransactionResourcePrices,
            @NonNull final ExchangeRate exchangeRate) {
        return TinybarValues.forTransactionWith(exchangeRate, topLevelResourcePrices, childTransactionResourcePrices);
    }

    @Provides
    @TransactionScope
    static SystemContractGasCalculator provideSystemContractGasCalculator(
            @NonNull final HandleContext context,
            @NonNull final CanonicalDispatchPrices canonicalDispatchPrices,
            @NonNull final TinybarValues tinybarValues) {
        return new SystemContractGasCalculator(
                tinybarValues, canonicalDispatchPrices, (body, payerId) -> context.dispatchComputeFees(
                                body, payerId, ComputeDispatchFeesAsTopLevel.NO)
                        .totalFee());
    }

    @Provides
    @TransactionScope
    static Instant provideConsensusTime(@NonNull final HandleContext context) {
        return requireNonNull(context).consensusNow();
    }

    @Provides
    @TransactionScope
    @TopLevelResourcePrices
    static FunctionalityResourcePrices provideTopLevelResourcePrices(
            @NonNull final HederaFunctionality functionality, @NonNull final HandleContext context) {
        return context.resourcePricesFor(functionality, SubType.DEFAULT);
    }

    @Provides
    @TransactionScope
    @ChildTransactionResourcePrices
    static FunctionalityResourcePrices provideChildTransactionResourcePrices(@NonNull final HandleContext context) {
        return context.resourcePricesFor(HederaFunctionality.CONTRACT_CALL, SubType.DEFAULT);
    }

    @Provides
    @TransactionScope
    static ExchangeRate provideExchangeRate(@NonNull final Instant now, @NonNull final HandleContext context) {
        return context.exchangeRateInfo().activeRate(now);
    }

    @Provides
    @Nullable
    @TransactionScope
    static HydratedEthTxData maybeProvideHydratedEthTxData(
            @NonNull final HandleContext context,
            @NonNull final EthereumCallDataHydration hydration,
            @NonNull final HederaConfig hederaConfig,
            @NonNull @InitialState final ReadableFileStore fileStore) {
        final var body = context.body();
        return body.hasEthereumTransaction()
                ? hydration.tryToHydrate(body.ethereumTransactionOrThrow(), fileStore, hederaConfig.firstUserEntity())
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
            @NonNull final HandleContext context,
            @NonNull final TinybarValues tinybarValues,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaOperations hederaOperations,
            @NonNull final HederaEvmBlocks hederaEvmBlocks,
            @NonNull final PendingCreationMetadataRef pendingCreationMetadataRef) {
        return new HederaEvmContext(
                hederaOperations.gasPriceInTinybars(),
                false,
                hederaEvmBlocks,
                tinybarValues,
                systemContractGasCalculator,
                context.recordBuilder(ContractOperationRecordBuilder.class),
                pendingCreationMetadataRef);
    }

    @Provides
    @TransactionScope
    static Supplier<HederaWorldUpdater> provideFeesOnlyUpdater(
            @NonNull final HederaWorldUpdater.Enhancement enhancement, @NonNull final EvmFrameStateFactory factory) {
        return () -> {
            enhancement.operations().begin();
            return new ProxyWorldUpdater(enhancement, requireNonNull(factory), null);
        };
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

    @Provides
    @TransactionScope
    static HederaWorldUpdater.Enhancement provideEnhancement(
            @NonNull final HederaOperations operations,
            @NonNull final HederaNativeOperations nativeOperations,
            @NonNull final SystemContractOperations systemContractOperations) {
        requireNonNull(operations);
        requireNonNull(nativeOperations);
        requireNonNull(systemContractOperations);
        return new HederaWorldUpdater.Enhancement(operations.begin(), nativeOperations, systemContractOperations);
    }

    @Binds
    @TransactionScope
    EvmFrameStateFactory bindEvmFrameStateFactory(ScopedEvmFrameStateFactory factory);

    @Binds
    @TransactionScope
    HederaOperations bindHederaOperations(HandleHederaOperations handleExtWorldScope);

    @Binds
    @TransactionScope
    HederaNativeOperations bindHederaNativeOperations(HandleHederaNativeOperations handleExtFrameScope);

    @Binds
    @TransactionScope
    SystemContractOperations bindSystemContractOperations(
            HandleSystemContractOperations handleSystemContractOperations);

    @Binds
    @TransactionScope
    HederaEvmBlocks bindHederaEvmBlocks(HandleContextHevmBlocks handleContextHevmBlocks);
}
