package com.hedera.node.app.service.contract.impl.exec.gas;

import com.hedera.node.app.service.contract.impl.hevm.HederaEvmContext;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransaction;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.HederaEvmAccount;
import com.hedera.node.app.spi.workflows.HandleException;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import javax.inject.Singleton;

@Singleton
public class CustomGasCharging {
    private final GasCalculator gasCalculator;

    public CustomGasCharging(@NonNull final GasCalculator gasCalculator) {
        this.gasCalculator = gasCalculator;
    }

    /**
     * Tries to charge gas for the given transaction based on the pre-fetched sender and relayer accounts,
     * within the given context and world updater.
     *
     * @param sender  the sender account
     * @param relayer the relayer account
     * @param context the context of the transaction, including the network gas price
     * @param worldUpdater the world updater for the transaction
     * @param transaction the transaction to charge gas for
     * @return the result of the gas charging
     * @throws HandleException if the gas charging fails fo
     */
    public GasChargingResult tryToChargeGas(
            @NonNull final HederaEvmAccount sender,
            @Nullable final HederaEvmAccount relayer,
            @NonNull final HederaEvmContext context,
            @NonNull final HederaWorldUpdater worldUpdater,
            @NonNull final HederaEvmTransaction transaction) {
        throw new AssertionError("Not implemented");
    }
}
