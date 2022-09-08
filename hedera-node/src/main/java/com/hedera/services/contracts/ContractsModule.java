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
import static org.hyperledger.besu.evm.MainnetEVMs.registerLondonOperations;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.contracts.annotations.BytecodeSource;
import com.hedera.services.contracts.annotations.StorageSource;
import com.hedera.services.contracts.gascalculator.GasCalculatorHederaV22;
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
import dagger.multibindings.StringKey;
import java.math.BigInteger;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

@Module(
        includes = {
            StoresModule.class,
            ContractsV_0_30Operations.class,
            ContractsV_0_31Operations.class
        })
public interface ContractsModule {

    @Qualifier
    @interface V_0_30 {}

    @Qualifier
    @interface V_0_31 {}

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
    @IntoMap
    @StringKey("v0.30")
    static EVM provideV30EVM(@V_0_30 Set<Operation> hederaOperations, GasCalculator gasCalculator) {
        var operationRegistry = new OperationRegistry();
        // ChainID will be overridden
        registerLondonOperations(operationRegistry, gasCalculator, BigInteger.ZERO);
        hederaOperations.forEach(operationRegistry::put);

        return new EVM(operationRegistry, gasCalculator, EvmConfiguration.DEFAULT);
    }

    @Provides
    @Singleton
    @IntoMap
    @StringKey("v0.31")
    static EVM provideV31EVM(@V_0_31 Set<Operation> hederaOperations, GasCalculator gasCalculator) {
        var operationRegistry = new OperationRegistry();
        // ChainID will be overridden
        registerLondonOperations(operationRegistry, gasCalculator, BigInteger.ZERO);
        hederaOperations.forEach(operationRegistry::put);

        return new EVM(operationRegistry, gasCalculator, EvmConfiguration.DEFAULT);
    }
}
