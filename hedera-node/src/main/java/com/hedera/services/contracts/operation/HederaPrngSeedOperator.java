package com.hedera.services.contracts.operation;

import com.hedera.services.txns.util.PrngLogic;
import java.util.Optional;
import java.util.OptionalLong;
import javax.inject.Inject;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.AbstractOperation;

public class HederaPrngSeedOperator extends AbstractOperation {

    private final OperationResult successResponse;
    private final PrngLogic prngLogic;

    @Inject
    public HederaPrngSeedOperator(PrngLogic prngLogic, GasCalculator gasCalculator) {
        super(0x44, "PRNGSEED", 0, 1, 1, gasCalculator);
        this.prngLogic = prngLogic;
        successResponse =
                new OperationResult(
                        OptionalLong.of(gasCalculator.getBaseTierGasCost()), Optional.empty());
    }

    @Override
    public OperationResult execute(MessageFrame frame, EVM evm) {
        Bytes seed = Bytes.wrap(prngLogic.getNMinus3RunningHashBytes());
        if (seed.size() > Bytes32.SIZE) {
            frame.pushStackItem(seed.slice(0, Bytes32.SIZE));
        } else {
            frame.pushStackItem(seed);
        }
        return successResponse;
    }
}
