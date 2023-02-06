/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.contracts;

import static com.hedera.node.app.service.mono.files.EntityExpiryMapFactory.entityExpiryMapFrom;
import static com.hedera.node.app.service.mono.store.contracts.precompile.ExchangeRatePrecompiledContract.EXCHANGE_RATE_SYSTEM_CONTRACT_ADDRESS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSPrecompiledContract.HTS_PRECOMPILED_CONTRACT_ADDRESS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.PrngSystemPrecompiledContract.PRNG_PRECOMPILE_ADDRESS;

import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.execution.CallLocalEvmTxProcessor;
import com.hedera.node.app.service.mono.contracts.execution.HederaMessageCallProcessor;
import com.hedera.node.app.service.mono.contracts.execution.LivePricesSource;
import com.hedera.node.app.service.mono.contracts.gascalculator.GasCalculatorHederaV22;
import com.hedera.node.app.service.mono.ledger.HederaLedger;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.validation.ContractStorageLimits;
import com.hedera.node.app.service.mono.state.validation.UsageLimits;
import com.hedera.node.app.service.mono.state.virtual.IterableStorageUtils;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.service.mono.store.StoresModule;
import com.hedera.node.app.service.mono.store.contracts.CodeCache;
import com.hedera.node.app.service.mono.store.contracts.EntityAccess;
import com.hedera.node.app.service.mono.store.contracts.HederaMutableWorldState;
import com.hedera.node.app.service.mono.store.contracts.HederaWorldState;
import com.hedera.node.app.service.mono.store.contracts.MutableEntityAccess;
import com.hedera.node.app.service.mono.store.contracts.SizeLimitedStorage;
import com.hedera.node.app.service.mono.store.contracts.precompile.ExchangeRatePrecompiledContract;
import com.hedera.node.app.service.mono.store.contracts.precompile.HTSPrecompiledContract;
import com.hedera.node.app.service.mono.store.contracts.precompile.InfrastructureFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.PrngSystemPrecompiledContract;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.virtualmap.VirtualMap;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import dagger.multibindings.StringKey;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.inject.Provider;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.contractvalidation.ContractValidationRule;
import org.hyperledger.besu.evm.contractvalidation.MaxCodeSizeRule;
import org.hyperledger.besu.evm.contractvalidation.PrefixCodeRule;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;

@Module(includes = {StoresModule.class, ContractsV_0_30Module.class, ContractsV_0_34Module.class})
public interface ContractsModule {

    @Qualifier
    @interface V_0_30 {}

    @Qualifier
    @interface V_0_34 {}

    @Binds
    @Singleton
    ContractStorageLimits provideContractStorageLimits(UsageLimits usageLimits);

    @Binds
    @Singleton
    HederaMutableWorldState provideMutableWorldState(HederaWorldState hederaWorldState);

    @Provides
    @Singleton
    static Map<EntityId, Long> provideEntityExpiries(Map<String, byte[]> blobStore) {
        return entityExpiryMapFrom(blobStore);
    }

    @Provides
    @Singleton
    static SizeLimitedStorage.IterableStorageUpserter provideStorageUpserter() {
        return IterableStorageUtils::overwritingUpsertMapping;
    }

    @Provides
    @Singleton
    static SizeLimitedStorage.IterableStorageRemover provideStorageRemover() {
        return IterableStorageUtils::removeMapping;
    }

    @Provides
    @Singleton
    static EntityAccess provideMutableEntityAccess(
            final AliasManager aliasManager,
            final HederaLedger ledger,
            final TransactionContext txnCtx,
            final SizeLimitedStorage storage,
            final TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger,
            final Supplier<VirtualMap<VirtualBlobKey, VirtualBlobValue>> bytecode) {
        return new MutableEntityAccess(
                ledger, aliasManager, txnCtx, storage, tokensLedger, bytecode);
    }

    @Binds
    @Singleton
    GasCalculator bindHederaGasCalculatorV20(GasCalculatorHederaV22 gasCalculator);

    @Binds
    @Singleton
    @IntoMap
    @StringKey(HTS_PRECOMPILED_CONTRACT_ADDRESS)
    PrecompiledContract bindHTSPrecompile(HTSPrecompiledContract htsPrecompiledContract);

    @Binds
    @Singleton
    @IntoMap
    @StringKey(EXCHANGE_RATE_SYSTEM_CONTRACT_ADDRESS)
    PrecompiledContract bindExchangeRatePrecompile(
            ExchangeRatePrecompiledContract exchangeRateContract);

    @Binds
    @Singleton
    @IntoMap
    @StringKey(PRNG_PRECOMPILE_ADDRESS)
    PrecompiledContract bindPrngPrecompile(PrngSystemPrecompiledContract prngSystemContract);

    @Provides
    @Singleton
    @IntoSet
    static ContractValidationRule provideMaxCodeSizeRule() {
        return MaxCodeSizeRule.of(0x6000);
    }

    @Provides
    @Singleton
    @IntoSet
    static ContractValidationRule providePrefixCodeRule() {
        return PrefixCodeRule.of();
    }

    @Provides
    @Singleton
    @IntoMap
    @StringKey(ContractsV_0_30Module.EVM_VERSION_0_30)
    static MessageCallProcessor provideV_0_30MessageCallProcessor(
            final @V_0_30 EVM evm,
            final @V_0_30 PrecompileContractRegistry precompiles,
            final Map<String, PrecompiledContract> hederaPrecompileList) {
        return new HederaMessageCallProcessor(evm, precompiles, hederaPrecompileList);
    }

    @Provides
    @Singleton
    @IntoMap
    @StringKey(ContractsV_0_30Module.EVM_VERSION_0_30)
    static ContractCreationProcessor provideV_0_30ContractCreateProcessor(
            final GasCalculator gasCalculator,
            final @V_0_30 EVM evm,
            Set<ContractValidationRule> validationRules) {
        return new ContractCreationProcessor(
                gasCalculator, evm, true, List.copyOf(validationRules), 1);
    }

    @Provides
    @Singleton
    @IntoMap
    @StringKey(ContractsV_0_34Module.EVM_VERSION_0_34)
    static MessageCallProcessor provideV_0_34MessageCallProcessor(
            final @V_0_34 EVM evm,
            final @V_0_34 PrecompileContractRegistry precompiles,
            final Map<String, PrecompiledContract> hederaPrecompileList,
            final InfrastructureFactory infrastructureFactory) {
        return new HederaMessageCallProcessor(
                evm, precompiles, hederaPrecompileList, infrastructureFactory);
    }

    @Provides
    @Singleton
    @IntoMap
    @StringKey(ContractsV_0_34Module.EVM_VERSION_0_34)
    static ContractCreationProcessor provideV_0_34ContractCreateProcessor(
            final GasCalculator gasCalculator,
            final @V_0_34 EVM evm,
            Set<ContractValidationRule> validationRules) {
        return new ContractCreationProcessor(
                gasCalculator, evm, true, List.copyOf(validationRules), 1);
    }

    @Provides
    @Singleton
    static Supplier<CallLocalEvmTxProcessor> provideCallLocalEvmTxProcessorFactory(
            final CodeCache codeCache,
            final LivePricesSource livePricesSource,
            final GlobalDynamicProperties dynamicProperties,
            final GasCalculator gasCalculator,
            final Map<String, Provider<MessageCallProcessor>> mcps,
            final Map<String, Provider<ContractCreationProcessor>> ccps,
            final AliasManager aliasManager) {
        return () ->
                new CallLocalEvmTxProcessor(
                        codeCache,
                        livePricesSource,
                        dynamicProperties,
                        gasCalculator,
                        mcps,
                        ccps,
                        aliasManager);
    }
}
