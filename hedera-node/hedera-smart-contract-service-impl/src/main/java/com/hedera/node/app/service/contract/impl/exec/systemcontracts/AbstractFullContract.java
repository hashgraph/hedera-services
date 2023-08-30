package com.hedera.node.app.service.contract.impl.exec.systemcontracts;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.AbstractPrecompiledContract;

import static java.util.Objects.requireNonNull;

public abstract class AbstractFullContract extends AbstractPrecompiledContract {
    protected AbstractFullContract(@NonNull final String name, @NonNull final GasCalculator gasCalculator) {
        super(requireNonNull(name), requireNonNull(gasCalculator));
    }

    @Override
    public long gasRequirement(final Bytes bytes) {
        throw new UnsupportedOperationException(getName() + " requires a MessageFrame to compute gas");
    }

    @Override
    public @NonNull PrecompileContractResult computePrecompile(final Bytes input, @NonNull final MessageFrame frame) {
        throw new UnsupportedOperationException(getName() + " only supports full results");
    }

}
