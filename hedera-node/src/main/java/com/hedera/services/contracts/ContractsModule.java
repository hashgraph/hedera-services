/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.contracts;

import static com.hedera.services.contracts.sources.AddressKeyedMapFactory.bytecodeMapFrom;
import static com.hedera.services.contracts.sources.AddressKeyedMapFactory.storageMapFrom;
import static com.hedera.services.files.EntityExpiryMapFactory.entityExpiryMapFrom;
import static com.hedera.services.store.contracts.precompile.ExchangeRatePrecompiledContract.EXCHANGE_RATE_SYSTEM_CONTRACT_ADDRESS;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.HTS_PRECOMPILED_CONTRACT_ADDRESS;
import static com.hedera.services.store.contracts.precompile.PrngSystemPrecompiledContract.PRNG_PRECOMPILE_ADDRESS;
import static org.hyperledger.besu.evm.operation.SStoreOperation.FRONTIER_MINIMUM;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.annotations.BytecodeSource;
import com.hedera.services.contracts.annotations.StorageSource;
import com.hedera.services.contracts.gascalculator.GasCalculatorHederaV22;
import com.hedera.services.contracts.operation.HederaBalanceOperation;
import com.hedera.services.contracts.operation.HederaCallCodeOperation;
import com.hedera.services.contracts.operation.HederaCallOperation;
import com.hedera.services.contracts.operation.HederaCreate2Operation;
import com.hedera.services.contracts.operation.HederaCreateOperation;
import com.hedera.services.contracts.operation.HederaDelegateCallOperation;
import com.hedera.services.contracts.operation.HederaExtCodeCopyOperation;
import com.hedera.services.contracts.operation.HederaExtCodeHashOperation;
import com.hedera.services.contracts.operation.HederaExtCodeSizeOperation;
import com.hedera.services.contracts.operation.HederaLogOperation;
import com.hedera.services.contracts.operation.HederaSLoadOperation;
import com.hedera.services.contracts.operation.HederaSStoreOperation;
import com.hedera.services.contracts.operation.HederaSelfDestructOperation;
import com.hedera.services.contracts.operation.HederaStaticCallOperation;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.validation.ContractStorageLimits;
import com.hedera.services.state.validation.UsageLimits;
import com.hedera.services.state.virtual.IterableStorageUtils;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.store.StoresModule;
import com.hedera.services.store.contracts.EntityAccess;
import com.hedera.services.store.contracts.HederaMutableWorldState;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.contracts.MutableEntityAccess;
import com.hedera.services.store.contracts.SizeLimitedStorage;
import com.hedera.services.store.contracts.precompile.ExchangeRatePrecompiledContract;
import com.hedera.services.store.contracts.precompile.HTSPrecompiledContract;
import com.hedera.services.store.contracts.precompile.PrngSystemPrecompiledContract;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.virtualmap.VirtualMap;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import dagger.multibindings.StringKey;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

@Module(includes = {StoresModule.class})
public interface ContractsModule {
    @Binds
    @Singleton
    ContractStorageLimits provideContractStorageLimits(UsageLimits usageLimits);

    @Binds
    @Singleton
    HederaMutableWorldState provideMutableWorldState(HederaWorldState hederaWorldState);

    @Provides
    @Singleton
    @BytecodeSource
    static Map<byte[], byte[]> provideBytecodeSource(Map<String, byte[]> blobStore) {
        return bytecodeMapFrom(blobStore);
    }

    @Provides
    @Singleton
    @StorageSource
    static Map<byte[], byte[]> provideStorageSource(Map<String, byte[]> blobStore) {
        return storageMapFrom(blobStore);
    }

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

    @Provides
    @Singleton
    @IntoSet
    static Operation provideLog0Operation(final GasCalculator gasCalculator) {
        return new HederaLogOperation(0, gasCalculator);
    }

    @Provides
    @Singleton
    @IntoSet
    static Operation provideLog1Operation(final GasCalculator gasCalculator) {
        return new HederaLogOperation(1, gasCalculator);
    }

    @Provides
    @Singleton
    @IntoSet
    static Operation provideLog2Operation(final GasCalculator gasCalculator) {
        return new HederaLogOperation(2, gasCalculator);
    }

    @Provides
    @Singleton
    @IntoSet
    static Operation provideLog3Operation(final GasCalculator gasCalculator) {
        return new HederaLogOperation(3, gasCalculator);
    }

    @Provides
    @Singleton
    @IntoSet
    static Operation provideLog4Operation(final GasCalculator gasCalculator) {
        return new HederaLogOperation(4, gasCalculator);
    }

    @Binds
    @Singleton
    GasCalculator bindHederaGasCalculatorV20(GasCalculatorHederaV22 gasCalculator);

    @Binds
    @Singleton
    @IntoSet
    Operation bindBalanceOperation(HederaBalanceOperation balance);

    @Binds
    @Singleton
    @IntoSet
    Operation bindCallCodeOperation(HederaCallCodeOperation callCode);

    @Binds
    @Singleton
    @IntoSet
    Operation bindCallOperation(HederaCallOperation call);

    @Binds
    @Singleton
    @IntoSet
    Operation bindCreateOperation(HederaCreateOperation create);

    @Binds
    @Singleton
    @IntoSet
    Operation bindCreate2Operation(HederaCreate2Operation create2);

    @Binds
    @Singleton
    @IntoSet
    Operation bindDelegateCallOperation(HederaDelegateCallOperation delegateCall);

    @Binds
    @Singleton
    @IntoSet
    Operation bindExtCodeCopyOperation(HederaExtCodeCopyOperation extCodeCopy);

    @Binds
    @Singleton
    @IntoSet
    Operation bindExtCodeHashOperation(HederaExtCodeHashOperation extCodeHash);

    @Binds
    @Singleton
    @IntoSet
    Operation bindExtCodeSizeOperation(HederaExtCodeSizeOperation extCodeSize);

    @Binds
    @Singleton
    @IntoSet
    Operation bindSelfDestructOperation(HederaSelfDestructOperation selfDestruct);

    @Provides
    @Singleton
    @IntoSet
    static Operation provideSStoreOperation(
            final GasCalculator gasCalculator, final GlobalDynamicProperties dynamicProperties) {
        return new HederaSStoreOperation(FRONTIER_MINIMUM, gasCalculator, dynamicProperties);
    }

    @Binds
    @Singleton
    @IntoSet
    Operation bindHederaSLoadOperation(HederaSLoadOperation sload);

    @Binds
    @Singleton
    @IntoSet
    Operation bindStaticCallOperation(HederaStaticCallOperation staticCall);

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
    static BiPredicate<Address, MessageFrame> provideAddressValidator(
            final Map<String, PrecompiledContract> precompiledContractMap) {
        final var precompiles =
                precompiledContractMap.keySet().stream()
                        .map(Address::fromHexString)
                        .collect(Collectors.toSet());
        return (address, frame) ->
                precompiles.contains(address) || frame.getWorldUpdater().get(address) != null;
    }
}
