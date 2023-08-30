package com.hedera.node.app.service.contract.impl.exec.systemcontracts;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import static java.util.Objects.requireNonNull;

public class HtsSystemContract extends AbstractFullContract implements HederaSystemContract  {
    private static final Logger log = LogManager.getLogger(HtsSystemContract.class);
    private static final String HTS_PRECOMPILE_NAME = "HTS";

    public static final String HTS_PRECOMPILE_ADDRESS = "0x167";

    public HtsSystemContract(@NonNull GasCalculator gasCalculator) {
        super(HTS_PRECOMPILE_NAME, gasCalculator);
    }

    @Override
    public FullResult computeFully(@NonNull Bytes input, @NonNull MessageFrame frame) {
        requireNonNull(input);
        requireNonNull(frame);

        throw new AssertionError("Not implemented");
    }
}
