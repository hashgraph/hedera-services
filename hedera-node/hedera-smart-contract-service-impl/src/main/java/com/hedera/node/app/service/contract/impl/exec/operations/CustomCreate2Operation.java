package com.hedera.node.app.service.contract.impl.exec.operations;

import com.hedera.node.app.service.contract.impl.exec.FeatureFlags;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

public class CustomCreate2Operation extends AbstractCustomCreateOperation {
    private final FeatureFlags featureFlags;

    public CustomCreate2Operation(
            @NonNull final GasCalculator gasCalculator,
            @NonNull final FeatureFlags featureFlags) {
        super(0xF5, "ħCREATE2", 4, 1, gasCalculator);
        this.featureFlags = featureFlags;
    }

    @Override
    protected boolean isEnabled(@NonNull final MessageFrame frame) {
        return featureFlags.isCreate2Enabled(frame);
    }

    @Override
    protected long cost(@NonNull final MessageFrame frame) {
        return gasCalculator().create2OperationGasCost(frame);
    }

    @Override
    protected Address setupPendingCreation(@NonNull final MessageFrame frame) {
        throw new AssertionError("Not implemented");
    }
}
