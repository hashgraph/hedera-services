/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static org.hyperledger.besu.evm.MainnetEVMs.registerParisOperations;
import static org.hyperledger.besu.evm.operation.SStoreOperation.FRONTIER_MINIMUM;

import com.hedera.node.app.service.evm.contracts.operations.HederaBalanceOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaDelegateCallOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaEvmChainIdOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaEvmCreate2Operation;
import com.hedera.node.app.service.evm.contracts.operations.HederaEvmCreateOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeCopyOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeHashOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeSizeOperation;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.ContractsModule.V_0_34;
import com.hedera.node.app.service.mono.contracts.execution.CallEvmTxProcessor;
import com.hedera.node.app.service.mono.contracts.execution.CallLocalEvmTxProcessor;
import com.hedera.node.app.service.mono.contracts.execution.CreateEvmTxProcessor;
import com.hedera.node.app.service.mono.contracts.execution.InHandleBlockMetaSource;
import com.hedera.node.app.service.mono.contracts.execution.LivePricesSource;
import com.hedera.node.app.service.mono.contracts.operation.HederaCallCodeOperation;
import com.hedera.node.app.service.mono.contracts.operation.HederaCallOperationV034;
import com.hedera.node.app.service.mono.contracts.operation.HederaLogOperation;
import com.hedera.node.app.service.mono.contracts.operation.HederaPrngSeedOperator;
import com.hedera.node.app.service.mono.contracts.operation.HederaSLoadOperation;
import com.hedera.node.app.service.mono.contracts.operation.HederaSStoreOperation;
import com.hedera.node.app.service.mono.contracts.operation.HederaSelfDestructOperation;
import com.hedera.node.app.service.mono.contracts.operation.HederaStaticCallOperation;
import com.hedera.node.app.service.mono.contracts.sources.EvmSigsVerifier;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.store.contracts.CodeCache;
import com.hedera.node.app.service.mono.store.contracts.HederaMutableWorldState;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import dagger.multibindings.StringKey;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.contractvalidation.ContractValidationRule;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.precompile.MainnetPrecompiledContracts;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;

@Module
public interface ContractsV_0_34Module {

    String EVM_VERSION_0_34 = "v0.34";

    @Provides
    @Singleton
    @V_0_34
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
    @V_0_34
    static Operation provideLog0Operation(final GasCalculator gasCalculator) {
        return new HederaLogOperation(0, gasCalculator);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_34
    static Operation provideLog1Operation(final GasCalculator gasCalculator) {
        return new HederaLogOperation(1, gasCalculator);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_34
    static Operation provideLog2Operation(final GasCalculator gasCalculator) {
        return new HederaLogOperation(2, gasCalculator);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_34
    static Operation provideLog3Operation(final GasCalculator gasCalculator) {
        return new HederaLogOperation(3, gasCalculator);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_34
    static Operation provideLog4Operation(final GasCalculator gasCalculator) {
        return new HederaLogOperation(4, gasCalculator);
    }

    @Binds
    @Singleton
    @IntoSet
    @V_0_34
    Operation bindChainIdOperation(HederaEvmChainIdOperation chainIdOperation);

    @Binds
    @Singleton
    @IntoSet
    @V_0_34
    Operation bindCreateOperation(HederaEvmCreateOperation createOperation);

    @Binds
    @Singleton
    @IntoSet
    @V_0_34
    Operation bindCreate2Operation(HederaEvmCreate2Operation create2Operation);

    @Provides
    @Singleton
    @IntoSet
    @V_0_34
    static Operation bindCallCodeOperation(
            final EvmSigsVerifier sigsVerifier,
            final GasCalculator gasCalculator,
            @V_0_34 final BiPredicate<Address, MessageFrame> addressValidator,
            final Map<String, PrecompiledContract> precompiledContractMap) {
        return new HederaCallCodeOperation(sigsVerifier, gasCalculator, addressValidator, precompiledContractMap);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_34
    static Operation bindCallOperation(
            final EvmSigsVerifier sigsVerifier,
            final GasCalculator gasCalculator,
            @V_0_34 final BiPredicate<Address, MessageFrame> addressValidator,
            final Map<String, PrecompiledContract> precompiledContractMap,
            final GlobalDynamicProperties globalDynamicProperties) {
        return new HederaCallOperationV034(
                sigsVerifier, gasCalculator, addressValidator, precompiledContractMap, globalDynamicProperties);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_34
    static Operation bindDelegateCallOperation(
            GasCalculator gasCalculator, @V_0_34 BiPredicate<Address, MessageFrame> addressValidator) {
        return new HederaDelegateCallOperation(gasCalculator, addressValidator);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_34
    static Operation bindStaticCallOperation(
            final GasCalculator gasCalculator,
            @V_0_34 final BiPredicate<Address, MessageFrame> addressValidator,
            final Map<String, PrecompiledContract> precompiledContractMap) {
        return new HederaStaticCallOperation(gasCalculator, addressValidator, precompiledContractMap);
    }

    @Provides
    @Singleton
    @IntoSet
    @ContractsModule.V_0_34
    static Operation bindBalanceOperation(
            GasCalculator gasCalculator, @ContractsModule.V_0_34 BiPredicate<Address, MessageFrame> addressValidator) {
        return new HederaBalanceOperation(gasCalculator, addressValidator);
    }

    @Provides
    @Singleton
    @IntoSet
    @ContractsModule.V_0_34
    static Operation bindExtCodeCopyOperation(
            GasCalculator gasCalculator, @ContractsModule.V_0_34 BiPredicate<Address, MessageFrame> addressValidator) {
        return new HederaExtCodeCopyOperation(gasCalculator, addressValidator);
    }

    @Provides
    @Singleton
    @IntoSet
    @ContractsModule.V_0_34
    static Operation bindExtCodeHashOperation(
            GasCalculator gasCalculator, @ContractsModule.V_0_34 BiPredicate<Address, MessageFrame> addressValidator) {
        return new HederaExtCodeHashOperation(gasCalculator, addressValidator);
    }

    @Provides
    @Singleton
    @IntoSet
    @ContractsModule.V_0_34
    static Operation bindExtCodeSizeOperation(
            GasCalculator gasCalculator, @ContractsModule.V_0_34 BiPredicate<Address, MessageFrame> addressValidator) {
        return new HederaExtCodeSizeOperation(gasCalculator, addressValidator);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_34
    static Operation bindSelfDestructOperation(
            GasCalculator gasCalculator,
            final TransactionContext txnCtx,
            /* Deliberately import the V_0_30 validator, we still want self-destructs to fail if the beneficiary is invalid */
            @ContractsModule.V_0_30 BiPredicate<Address, MessageFrame> addressValidator,
            final EvmSigsVerifier sigsVerifier) {
        return new HederaSelfDestructOperation(gasCalculator, txnCtx, addressValidator, sigsVerifier);
    }

    @Provides
    @Singleton
    @IntoSet
    @V_0_34
    static Operation provideSStoreOperation(
            final GasCalculator gasCalculator, final GlobalDynamicProperties dynamicProperties) {
        return new HederaSStoreOperation(FRONTIER_MINIMUM, gasCalculator, dynamicProperties);
    }

    @Binds
    @Singleton
    @IntoSet
    @V_0_34
    Operation bindHederaSLoadOperation(HederaSLoadOperation sLoadOperation);

    @Binds
    @Singleton
    @IntoSet
    @V_0_34
    Operation bindHederaPrngSeedOperation(HederaPrngSeedOperator prngSeedOperator);

    @Provides
    @Singleton
    @V_0_34
    static EVM provideV_0_34EVM(
            @V_0_34 Set<Operation> hederaOperations, GasCalculator gasCalculator, EvmConfiguration evmConfiguration) {
        var operationRegistry = new OperationRegistry();
        // ChainID will be overridden
        registerParisOperations(operationRegistry, gasCalculator, BigInteger.ZERO);
        hederaOperations.forEach(operationRegistry::put);
        return new EVM(operationRegistry, gasCalculator, evmConfiguration, EvmSpecVersion.PARIS);
    }

    @Provides
    @Singleton
    @V_0_34
    static PrecompileContractRegistry providePrecompiledContractRegistry(GasCalculator gasCalculator) {
        final var precompileContractRegistry = new PrecompileContractRegistry();
        MainnetPrecompiledContracts.populateForIstanbul(precompileContractRegistry, gasCalculator);
        return precompileContractRegistry;
    }

    @Provides
    @Singleton
    @IntoMap
    @StringKey(ContractsV_0_34Module.EVM_VERSION_0_34)
    static Supplier<CallLocalEvmTxProcessor> provideV_0_34CallLocalEvmTxProcessor(
            final CodeCache codeCache,
            final LivePricesSource livePricesSource,
            final GlobalDynamicProperties dynamicProperties,
            final GasCalculator gasCalculator,
            final @V_0_34 Provider<MessageCallProcessor> mcp,
            final @V_0_34 Provider<ContractCreationProcessor> ccp,
            final AliasManager aliasManager) {
        return () -> new CallLocalEvmTxProcessor(
                codeCache, livePricesSource, dynamicProperties, gasCalculator, mcp, ccp, aliasManager);
    }

    @Provides
    @Singleton
    @V_0_34
    static ContractCreationProcessor provideV_0_34ContractCreateProcessor(
            final GasCalculator gasCalculator, final @V_0_34 EVM evm, Set<ContractValidationRule> validationRules) {
        return new ContractCreationProcessor(gasCalculator, evm, true, List.copyOf(validationRules), 1);
    }

    @Provides
    @Singleton
    @IntoMap
    @StringKey(ContractsV_0_34Module.EVM_VERSION_0_34)
    static Supplier<CallEvmTxProcessor> provideV_0_34CallEvmTxProcessor(
            final HederaMutableWorldState worldState,
            final LivePricesSource livePricesSource,
            final CodeCache codeCache,
            final GlobalDynamicProperties dynamicProperties,
            final GasCalculator gasCalculator,
            final @V_0_34 Provider<MessageCallProcessor> mcp,
            final @V_0_34 Provider<ContractCreationProcessor> ccp,
            final AliasManager aliasManager,
            final InHandleBlockMetaSource blockMetaSource) {
        return () -> new CallEvmTxProcessor(
                worldState,
                livePricesSource,
                codeCache,
                dynamicProperties,
                gasCalculator,
                mcp,
                ccp,
                aliasManager,
                blockMetaSource);
    }

    @Provides
    @Singleton
    @IntoMap
    @StringKey(ContractsV_0_34Module.EVM_VERSION_0_34)
    static Supplier<CreateEvmTxProcessor> provideV_0_34CCreateEvmTxProcessor(
            final HederaMutableWorldState worldState,
            final LivePricesSource livePricesSource,
            final CodeCache codeCache,
            final GlobalDynamicProperties dynamicProperties,
            final GasCalculator gasCalculator,
            final @V_0_34 Provider<MessageCallProcessor> mcp,
            final @V_0_34 Provider<ContractCreationProcessor> ccp,
            final AliasManager aliasManager,
            final InHandleBlockMetaSource blockMetaSource) {
        return () -> new CreateEvmTxProcessor(
                worldState,
                livePricesSource,
                codeCache,
                dynamicProperties,
                gasCalculator,
                mcp,
                ccp,
                aliasManager,
                blockMetaSource);
    }
}
