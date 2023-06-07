package com.hedera.node.app.service.contract.impl.exec.operations;

import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

public class CustomCreateOperation extends AbstractCustomCreateOperation {
    public CustomCreateOperation(@NonNull final GasCalculator gasCalculator) {
        super(0xF0, "ħCREATE", 3, 1, gasCalculator);
    }

    @Override
    protected boolean isEnabled(@NonNull final MessageFrame frame) {
        return true;
    }

    @Override
    protected long cost(@NonNull final MessageFrame frame) {
        return gasCalculator().createOperationGasCost(frame);
    }

    @Override
    protected @NonNull Address setupPendingCreation(@NonNull final MessageFrame frame) {
        final var updater = (ProxyWorldUpdater) frame.getWorldUpdater();
        final var address = updater.setupCreate(frame.getRecipientAddress());
        frame.warmUpAddress(address);
        return address;
    }
}
