package com.hedera.node.app.service.contract.impl.exec.operations;

import com.hedera.node.app.service.contract.impl.exec.AddressChecks;
import com.hedera.node.app.service.contract.impl.exec.FeatureFlags;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.CallOperation;

/**
 * A small customization of {@link CallOperation} that, if lazy creation is enabled and will
 * apply to this call, does no additional address checks. Otherwise, only allows calls to an
 * address that is either a Hedera precompile, a system address, or not missing.
 *
 * <p>(Of course, any Hedera precompile address <i>is</i> a system address, but until v0.38
 * the {@link AddressChecks#isSystemAccount(Address)} will always return false, so we need
 * to do the more narrow check for precompiles separately.)
 */
public class CustomCallOperation extends CallOperation {
    private final FeatureFlags featureFlags;
    private final AddressChecks addressChecks;

    public CustomCallOperation(
            @NonNull final FeatureFlags featureFlags,
            @NonNull final GasCalculator gasCalculator,
            @NonNull final AddressChecks addressChecks) {
        super(gasCalculator);
        this.featureFlags = featureFlags;
        this.addressChecks = addressChecks;
    }

    @Override
    public OperationResult execute(@NonNull final MessageFrame frame, @NonNull final EVM evm) {
        throw new AssertionError("Not implemented");
    }
}
