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

package com.hedera.node.app.service.contract.impl.exec.v034;

import static org.hyperledger.besu.evm.MainnetEVMs.registerLondonOperations;

import com.hedera.node.app.service.contract.impl.annotations.ServicesV034;
import com.hedera.node.app.service.contract.impl.exec.AddressChecks;
import com.hedera.node.app.service.contract.impl.exec.FeatureFlags;
import com.hedera.node.app.service.contract.impl.exec.TransactionProcessor;
import com.hedera.node.app.service.contract.impl.exec.operations.CustomBalanceOperation;
import com.hedera.node.app.service.contract.impl.exec.operations.CustomCallOperation;
import com.hedera.node.app.service.contract.impl.exec.operations.CustomChainIdOperation;
import com.hedera.node.app.service.contract.impl.exec.v030.SystemAgnosticAddressChecks;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.Map;
import java.util.Set;
import javax.inject.Singleton;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.precompile.MainnetPrecompiledContracts;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;

/**
 * Provides the Services 0.34 EVM implementation, which consists of Paris operations and
 * Instanbul precompiles plus the Hedera gas calculator, system contracts, and operations
 * as they were configured in the 0.34 release (in particular, with the option for lazy
 * creation, but without special treatment for system addresses).
 */
@Module
public interface V034Module {
    @Provides
    @Singleton
    @ServicesV034
    static TransactionProcessor provideTransactionProcessor(
            @ServicesV034 @NonNull final MessageCallProcessor messageCallProcessor,
            @ServicesV034 @NonNull final ContractCreationProcessor contractCreationProcessor,
            @NonNull final GasCalculator gasCalculator) {
        return new TransactionProcessor(gasCalculator, messageCallProcessor, contractCreationProcessor);
    }

    @Provides
    @Singleton
    @ServicesV034
    static ContractCreationProcessor provideContractCreationProcessor(
            @ServicesV034 @NonNull final EVM evm,
            @ServicesV034 @NonNull final PrecompileContractRegistry precompileContractRegistry,
            @NonNull final Map<String, PrecompiledContract> hederaSystemContracts) {
        throw new AssertionError("Not implemented");
    }

    @Provides
    @Singleton
    @ServicesV034
    static MessageCallProcessor provideMessageCallProcessor(
            @ServicesV034 @NonNull final EVM evm,
            @ServicesV034 @NonNull final PrecompileContractRegistry precompileContractRegistry,
            @NonNull final Map<String, PrecompiledContract> hederaSystemContracts) {
        throw new AssertionError("Not implemented");
    }

    @Provides
    @Singleton
    @ServicesV034
    static EVM provideEVM(
            @ServicesV034 @NonNull final Set<Operation> customOperations, @NonNull final GasCalculator gasCalculator) {
        // Use Paris EVM with 0.34 custom operations and 0x00 chain id (set at runtime)
        final var operationRegistry = new OperationRegistry();
        registerLondonOperations(operationRegistry, gasCalculator, BigInteger.ZERO);
        customOperations.forEach(operationRegistry::put);
        return new EVM(operationRegistry, gasCalculator, EvmConfiguration.DEFAULT, EvmSpecVersion.PARIS);
    }

    @Provides
    @Singleton
    @ServicesV034
    static PrecompileContractRegistry providePrecompileContractRegistry(@NonNull final GasCalculator gasCalculator) {
        final var precompileContractRegistry = new PrecompileContractRegistry();
        MainnetPrecompiledContracts.populateForIstanbul(precompileContractRegistry, gasCalculator);
        return precompileContractRegistry;
    }

    @Binds
    @ServicesV034
    FeatureFlags bindFeatureFlags(ContextualFeatureFlags featureFlags);

    @Binds
    @ServicesV034
    AddressChecks bindAddressChecks(SystemAgnosticAddressChecks addressChecks);

    @Provides
    @IntoSet
    @ServicesV034
    static Operation provideBalanceOperation(
            @NonNull final GasCalculator gasCalculator, @ServicesV034 @NonNull final AddressChecks addressChecks) {
        return new CustomBalanceOperation(gasCalculator, addressChecks);
    }

    @Provides
    @IntoSet
    @ServicesV034
    static Operation provideChainIdOperation(@NonNull final GasCalculator gasCalculator) {
        return new CustomChainIdOperation(gasCalculator);
    }

    @Provides
    @IntoSet
    @ServicesV034
    static Operation provideCallOperation(
            @NonNull final GasCalculator gasCalculator,
            @ServicesV034 @NonNull final FeatureFlags featureFlags,
            @ServicesV034 @NonNull final AddressChecks addressChecks) {
        return new CustomCallOperation(featureFlags, gasCalculator, addressChecks);
    }
}
