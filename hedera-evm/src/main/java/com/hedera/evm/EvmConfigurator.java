package com.hedera.evm;

import com.hedera.evm.execution.BlockMetaSource;
import com.hedera.evm.execution.EvmProperties;
import com.hedera.evm.execution.HederaMutableWorldState;
import java.util.Map;
import java.util.Set;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

/**
 * <t>WARNING</t> Before using the hedera-evm library, please first configure the interface
 * implementations that would be used inside, via this configuration class.
 *
 * Configuration class used to set up concrete interface implementations, that would be used
 * under this library. In this way we can tailor the behaviour of the library based on the client
 * that uses it.
 */
public class EvmConfigurator {

    public static BlockMetaSource blockMetaSource;
    public static GasCalculator gasCalculator;
    public static EvmProperties evmProperties;
    public static Set<Operation> hederaOperations;
    public static Map<String, PrecompiledContract> precompiledContractMap;
    public static HederaMutableWorldState worldState;

    public static void setBlockMetaSource(final BlockMetaSource blockMetaSource) {
        EvmConfigurator.blockMetaSource = blockMetaSource;
    }

    public static void setGasCalculator(final GasCalculator gasCalculator) {
        EvmConfigurator.gasCalculator = gasCalculator;
    }

    public static void setEvmProperties(final EvmProperties evmProperties) {
        EvmConfigurator.evmProperties = evmProperties;
    }

    public static void setHederaOperations(final Set<Operation> hederaOperations) {
        EvmConfigurator.hederaOperations = hederaOperations;
    }

    public static void setPrecompiledContractMap(
            final Map<String, PrecompiledContract> precompiledContractMap) {
        EvmConfigurator.precompiledContractMap = precompiledContractMap;
    }

    public static void setWorldState(final HederaMutableWorldState worldState) {
      EvmConfigurator.worldState = worldState;
    }
}
