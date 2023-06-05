package com.hedera.node.app.service.contract.impl.exec.v030;

import com.hedera.node.app.service.contract.impl.annotations.ServicesV030;
import com.hedera.node.app.service.contract.impl.exec.AddressChecks;
import com.hedera.node.app.service.contract.impl.exec.FeatureFlags;
import com.hedera.node.app.service.contract.impl.exec.TransactionProcessor;
import com.hedera.node.app.service.contract.impl.exec.operations.CustomBalanceOperation;
import com.hedera.node.app.service.contract.impl.exec.operations.CustomChainIdOperation;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import edu.umd.cs.findbugs.annotations.NonNull;
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

import javax.inject.Singleton;
import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

import static org.hyperledger.besu.evm.MainnetEVMs.registerLondonOperations;

/**
 * Provides the Services 0.30 EVM implementation, which consists of London operations and
 * Instanbul precompiles plus the Hedera gas calculator, system contracts, and operations
 * as they were configured in the 0.30 release (in particular, without lazy creation and
 * without special treatment to make system addresses "invisible").
 */
@Module
public interface V030Module {
    @Provides
    @Singleton
    @ServicesV030
    static TransactionProcessor provideTransactionProcessor(
            @ServicesV030 @NonNull final MessageCallProcessor messageCallProcessor,
            @ServicesV030 @NonNull final ContractCreationProcessor contractCreationProcessor,
            @NonNull final GasCalculator gasCalculator) {
        return new TransactionProcessor(gasCalculator, messageCallProcessor, contractCreationProcessor);
    }

    @Provides
    @Singleton
    @ServicesV030
    static ContractCreationProcessor provideContractCreationProcessor(
            @ServicesV030 @NonNull final EVM evm,
            @ServicesV030 @NonNull final PrecompileContractRegistry precompileContractRegistry,
            @NonNull final Map<String, PrecompiledContract> hederaSystemContracts) {
        throw new AssertionError("Not implemented");
    }

    @Provides
    @Singleton
    @ServicesV030
    static MessageCallProcessor provideMessageCallProcessor(
            @ServicesV030 @NonNull final EVM evm,
            @ServicesV030 @NonNull final PrecompileContractRegistry precompileContractRegistry,
            @NonNull final Map<String, PrecompiledContract> hederaSystemContracts) {
        throw new AssertionError("Not implemented");
    }

    @Provides
    @Singleton
    @ServicesV030
    static EVM provideEVM(
            @ServicesV030 @NonNull final Set<Operation> customOperations,
            @NonNull final GasCalculator gasCalculator) {
        // Use London EVM with 0.30 custom operations and 0x00 chain id (set at runtime)
        final var operationRegistry = new OperationRegistry();
        registerLondonOperations(operationRegistry, gasCalculator, BigInteger.ZERO);
        customOperations.forEach(operationRegistry::put);
        return new EVM(operationRegistry, gasCalculator, EvmConfiguration.DEFAULT, EvmSpecVersion.LONDON);
    }

    @Provides
    @Singleton
    @ServicesV030
    static PrecompileContractRegistry providePrecompileContractRegistry(@NonNull final GasCalculator gasCalculator) {
        final var precompileContractRegistry = new PrecompileContractRegistry();
        MainnetPrecompiledContracts.populateForIstanbul(precompileContractRegistry, gasCalculator);
        return precompileContractRegistry;
    }

    @Binds
    @ServicesV030
    FeatureFlags bindFeatureFlags(DisabledFeatureFlags featureFlags);

    @Binds
    @ServicesV030
    AddressChecks bindAddressChecks(SystemAgnosticAddressChecks addressChecks);

    @Provides
    @IntoSet
    @ServicesV030
    static Operation provideBalanceOperation(
            @NonNull final GasCalculator gasCalculator,
            @ServicesV030 @NonNull final AddressChecks addressChecks) {
        return new CustomBalanceOperation(gasCalculator, addressChecks);
    }

    @Provides
    @IntoSet
    @ServicesV030
    static Operation provideChainIdOperation(@NonNull final GasCalculator gasCalculator) {
        return new CustomChainIdOperation(gasCalculator);
    }
}
