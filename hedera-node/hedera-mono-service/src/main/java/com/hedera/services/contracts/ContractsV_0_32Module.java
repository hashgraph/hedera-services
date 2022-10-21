/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import static org.hyperledger.besu.evm.MainnetEVMs.registerParisOperations;
import static org.hyperledger.besu.evm.operation.SStoreOperation.FRONTIER_MINIMUM;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.ContractsModule.V_0_30;
import com.hedera.services.contracts.ContractsModule.V_0_32;
import com.hedera.services.contracts.operation.HederaCallCodeOperation;
import com.hedera.services.contracts.operation.HederaCallOperation;
import com.hedera.services.contracts.operation.HederaChainIdOperation;
import com.hedera.services.contracts.operation.HederaCreate2Operation;
import com.hedera.services.contracts.operation.HederaCreateOperation;
import com.hedera.services.contracts.operation.HederaDelegateCallOperation;
import com.hedera.services.contracts.operation.HederaLogOperation;
import com.hedera.services.contracts.operation.HederaPrngSeedOperator;
import com.hedera.services.contracts.operation.HederaSLoadOperation;
import com.hedera.services.contracts.operation.HederaSStoreOperation;
import com.hedera.services.contracts.operation.HederaSelfDestructOperation;
import com.hedera.services.contracts.operation.HederaStaticCallOperation;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import java.math.BigInteger;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import javax.inject.Singleton;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.precompile.MainnetPrecompiledContracts;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

@Module
public interface ContractsV_0_32Module {

    String EVM_VERSION_0_32 = "v0.32";

    @Provides
    @Singleton
    @V_0_32
    static BiPredicate<Address, MessageFrame> provideAddressValidator(
            final Map<String, PrecompiledContract> precompiledContractMap) {
        return (address, frame) -> true;
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_32
    static Operation provideLog0Operation(final GasCalculator gasCalculator) {
        return new HederaLogOperation(0, gasCalculator);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_32
    static Operation provideLog1Operation(final GasCalculator gasCalculator) {
        return new HederaLogOperation(1, gasCalculator);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_32
    static Operation provideLog2Operation(final GasCalculator gasCalculator) {
        return new HederaLogOperation(2, gasCalculator);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_32
    static Operation provideLog3Operation(final GasCalculator gasCalculator) {
        return new HederaLogOperation(3, gasCalculator);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_32
    static Operation provideLog4Operation(final GasCalculator gasCalculator) {
        return new HederaLogOperation(4, gasCalculator);
    }

    @Binds
    @Singleton
    @IntoSet
    @V_0_32
    Operation bindChainIdOperation(HederaChainIdOperation chainIdOperation);

    @Binds
    @Singleton
    @IntoSet
    @V_0_32
    Operation bindCreateOperation(HederaCreateOperation createOperation);

    @Binds
    @Singleton
    @IntoSet
    @V_0_32
    Operation bindCreate2Operation(HederaCreate2Operation create2Operation);

    @Provides
    @Singleton
    @IntoSet
    @V_0_32
    static Operation bindCallCodeOperation(
            final EvmSigsVerifier sigsVerifier,
            final GasCalculator gasCalculator,
            @V_0_32 final BiPredicate<Address, MessageFrame> addressValidator,
            final Map<String, PrecompiledContract> precompiledContractMap) {
        return new HederaCallCodeOperation(
                sigsVerifier, gasCalculator, addressValidator, precompiledContractMap);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_32
    static Operation bindCallOperation(
            final EvmSigsVerifier sigsVerifier,
            final GasCalculator gasCalculator,
            @V_0_32 final BiPredicate<Address, MessageFrame> addressValidator,
            final Map<String, PrecompiledContract> precompiledContractMap) {
        return new HederaCallOperation(
                sigsVerifier, gasCalculator, addressValidator, precompiledContractMap);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_32
    static Operation bindDelegateCallOperation(
            GasCalculator gasCalculator,
            @V_0_32 BiPredicate<Address, MessageFrame> addressValidator) {
        return new HederaDelegateCallOperation(gasCalculator, addressValidator);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_32
    static Operation bindStaticCallOperation(
            final GasCalculator gasCalculator,
            final EvmSigsVerifier sigsVerifier,
            @V_0_32 final BiPredicate<Address, MessageFrame> addressValidator,
            final Map<String, PrecompiledContract> precompiledContractMap) {
        return new HederaStaticCallOperation(
                gasCalculator, sigsVerifier, addressValidator, precompiledContractMap);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_32
    static Operation bindSelfDestructOperation(
            GasCalculator gasCalculator,
            final TransactionContext txnCtx,
            /* Deliberately import the V_0_30 validator, we still want self-destructs to fail if the beneficiary is invalid */
            @V_0_30 BiPredicate<Address, MessageFrame> addressValidator) {
        return new HederaSelfDestructOperation(gasCalculator, txnCtx, addressValidator);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_32
    static Operation provideSStoreOperation(
            final GasCalculator gasCalculator, final GlobalDynamicProperties dynamicProperties) {
        return new HederaSStoreOperation(FRONTIER_MINIMUM, gasCalculator, dynamicProperties);
    }

    @Binds
    @Singleton
    @IntoSet
    @V_0_32
    Operation bindHederaSLoadOperation(HederaSLoadOperation sLoadOperation);

    @Binds
    @Singleton
    @IntoSet
    @V_0_32
    Operation bindHederaPrngSeedOperation(HederaPrngSeedOperator prngSeedOperator);

    @Provides
    @Singleton
    @V_0_32
    static EVM provideV_0_32EVM(
            @V_0_32 Set<Operation> hederaOperations, GasCalculator gasCalculator) {
        var operationRegistry = new OperationRegistry();
        // ChainID will be overridden
        registerParisOperations(operationRegistry, gasCalculator, BigInteger.ZERO);
        hederaOperations.forEach(operationRegistry::put);
        return new EVM(operationRegistry, gasCalculator, EvmConfiguration.DEFAULT);
    }

    @Provides
    @Singleton
    @V_0_32
    static PrecompileContractRegistry providePrecompiledContractRegistry(
            GasCalculator gasCalculator) {
        final var precompileContractRegistry = new PrecompileContractRegistry();
        MainnetPrecompiledContracts.populateForIstanbul(precompileContractRegistry, gasCalculator);
        return precompileContractRegistry;
    }
}
