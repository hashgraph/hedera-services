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

import static org.hyperledger.besu.evm.MainnetEVMs.registerLondonOperations;
import static org.hyperledger.besu.evm.operation.SStoreOperation.FRONTIER_MINIMUM;

import com.hedera.node.app.service.evm.contracts.operations.HederaBalanceOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaDelegateCallOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeCopyOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeHashOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeSizeOperation;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.ContractsModule.V_0_30;
import com.hedera.node.app.service.mono.contracts.operation.HederaCallCodeOperation;
import com.hedera.node.app.service.mono.contracts.operation.HederaCallOperation;
import com.hedera.node.app.service.mono.contracts.operation.HederaChainIdOperation;
import com.hedera.node.app.service.mono.contracts.operation.HederaCreate2Operation;
import com.hedera.node.app.service.mono.contracts.operation.HederaCreateOperation;
import com.hedera.node.app.service.mono.contracts.operation.HederaLogOperation;
import com.hedera.node.app.service.mono.contracts.operation.HederaSLoadOperation;
import com.hedera.node.app.service.mono.contracts.operation.HederaSStoreOperation;
import com.hedera.node.app.service.mono.contracts.operation.HederaSelfDestructOperation;
import com.hedera.node.app.service.mono.contracts.operation.HederaStaticCallOperation;
import com.hedera.node.app.service.mono.contracts.sources.EvmSigsVerifier;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import java.math.BigInteger;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Named;
import javax.inject.Singleton;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.precompile.MainnetPrecompiledContracts;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

@Module
public interface ContractsV_0_30Module {

    String EVM_VERSION_0_30 = "v0.30";

    @Provides
    @Singleton
    @V_0_30
    static BiPredicate<Address, MessageFrame> provideAddressValidator(
            final Map<String, PrecompiledContract> precompiledContractMap) {
        final var precompiles = precompiledContractMap.keySet().stream()
                .map(Address::fromHexString)
                .collect(Collectors.toSet());
        return (address, frame) ->
                precompiles.contains(address) || frame.getWorldUpdater().get(address) != null;
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_30
    static Operation provideLog0Operation(final GasCalculator gasCalculator) {
        return new HederaLogOperation(0, gasCalculator);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_30
    static Operation provideLog1Operation(final GasCalculator gasCalculator) {
        return new HederaLogOperation(1, gasCalculator);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_30
    static Operation provideLog2Operation(final GasCalculator gasCalculator) {
        return new HederaLogOperation(2, gasCalculator);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_30
    static Operation provideLog3Operation(final GasCalculator gasCalculator) {
        return new HederaLogOperation(3, gasCalculator);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_30
    static Operation provideLog4Operation(final GasCalculator gasCalculator) {
        return new HederaLogOperation(4, gasCalculator);
    }

    @Binds
    @Singleton
    @IntoSet
    @V_0_30
    Operation bindChainIdOperation(HederaChainIdOperation chainIdOperation);

    @Binds
    @Singleton
    @IntoSet
    @V_0_30
    Operation bindCreateOperation(HederaCreateOperation createOperation);

    @Binds
    @Singleton
    @IntoSet
    @V_0_30
    Operation bindCreate2Operation(HederaCreate2Operation create2Operation);

    @Provides
    @Singleton
    @IntoSet
    @V_0_30
    static Operation bindCallCodeOperation(
            final EvmSigsVerifier sigsVerifier,
            final GasCalculator gasCalculator,
            @V_0_30 final BiPredicate<Address, MessageFrame> addressValidator,
            final @Named("PrecompileDetector") Predicate<Address> precompileDetector,
            @ContractsModule.V_0_30 final PrecompileContractRegistry precompileContractRegistry) {
        return new HederaCallCodeOperation(
                sigsVerifier, gasCalculator, addressValidator, precompileDetector, precompileContractRegistry);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_30
    static Operation bindCallOperation(
            final EvmSigsVerifier sigsVerifier,
            final GasCalculator gasCalculator,
            @V_0_30 final BiPredicate<Address, MessageFrame> addressValidator,
            final @Named("PrecompileDetector") Predicate<Address> precompileDetector,
            @ContractsModule.V_0_30 final PrecompileContractRegistry precompileContractRegistry) {
        return new HederaCallOperation(
                sigsVerifier, gasCalculator, addressValidator, precompileDetector, precompileContractRegistry);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_30
    static Operation bindDelegateCallOperation(
            GasCalculator gasCalculator,
            @V_0_30 BiPredicate<Address, MessageFrame> addressValidator,
            @ContractsModule.V_0_30 final PrecompileContractRegistry precompileContractRegistry) {
        return new HederaDelegateCallOperation(gasCalculator, addressValidator, precompileContractRegistry);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_30
    static Operation bindStaticCallOperation(
            final GasCalculator gasCalculator,
            @V_0_30 final BiPredicate<Address, MessageFrame> addressValidator,
            final @Named("PrecompileDetector") Predicate<Address> precompileDetector,
            @ContractsModule.V_0_30 final PrecompileContractRegistry precompileContractRegistry) {
        return new HederaStaticCallOperation(
                gasCalculator, addressValidator, precompileDetector, precompileContractRegistry);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_30
    static Operation bindBalanceOperation(
            GasCalculator gasCalculator, @V_0_30 BiPredicate<Address, MessageFrame> addressValidator) {
        return new HederaBalanceOperation(gasCalculator, addressValidator);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_30
    static Operation bindExtCodeCopyOperation(
            GasCalculator gasCalculator, @V_0_30 BiPredicate<Address, MessageFrame> addressValidator) {
        return new HederaExtCodeCopyOperation(gasCalculator, addressValidator);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_30
    static Operation bindExtCodeHashOperation(
            GasCalculator gasCalculator, @V_0_30 BiPredicate<Address, MessageFrame> addressValidator) {
        return new HederaExtCodeHashOperation(gasCalculator, addressValidator);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_30
    static Operation bindExtCodeSizeOperation(
            GasCalculator gasCalculator, @V_0_30 BiPredicate<Address, MessageFrame> addressValidator) {
        return new HederaExtCodeSizeOperation(gasCalculator, addressValidator);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_30
    static Operation bindSelfDestructOperation(
            GasCalculator gasCalculator,
            final TransactionContext txnCtx,
            @V_0_30 BiPredicate<Address, MessageFrame> addressValidator,
            final EvmSigsVerifier evmSigsVerifier) {
        return new HederaSelfDestructOperation(gasCalculator, txnCtx, addressValidator, evmSigsVerifier);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_30
    static Operation provideSStoreOperation(
            final GasCalculator gasCalculator, final GlobalDynamicProperties dynamicProperties) {
        return new HederaSStoreOperation(FRONTIER_MINIMUM, gasCalculator, dynamicProperties);
    }

    @Binds
    @Singleton
    @IntoSet
    @V_0_30
    Operation bindHederaSLoadOperation(HederaSLoadOperation sLoadOperation);

    @Provides
    @Singleton
    @V_0_30
    static EVM provideV_0_30EVM(@V_0_30 Set<Operation> hederaOperations, GasCalculator gasCalculator) {
        var operationRegistry = new OperationRegistry();
        // ChainID will be overridden
        registerLondonOperations(operationRegistry, gasCalculator, BigInteger.ZERO);
        hederaOperations.forEach(operationRegistry::put);

        return new EVM(operationRegistry, gasCalculator, EvmConfiguration.DEFAULT, EvmSpecVersion.LONDON);
    }

    @Provides
    @Singleton
    @V_0_30
    static PrecompileContractRegistry providePrecompiledContractRegistry(
            GasCalculator gasCalculator, final Map<String, PrecompiledContract> hederaPrecompiles) {
        final var precompileContractRegistry = new PrecompileContractRegistry();
        MainnetPrecompiledContracts.populateForIstanbul(precompileContractRegistry, gasCalculator);
        hederaPrecompiles.forEach((k, v) -> precompileContractRegistry.put(Address.fromHexString(k), v));
        return precompileContractRegistry;
    }
}
