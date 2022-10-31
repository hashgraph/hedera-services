package com.hedera.services.evm.contracts.operations;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.CallOperation;

import java.util.function.BiPredicate;

public class HederaEvmCallOperation extends CallOperation {

    protected final BiPredicate<Address, MessageFrame> addressValidator;

    public HederaEvmCallOperation(
            final GasCalculator gasCalculator,
            final BiPredicate<Address, MessageFrame> addressValidator) {
        super(gasCalculator);
        this.addressValidator = addressValidator;
    }

    @Override
    public OperationResult execute(MessageFrame frame, EVM evm) {
        return HederaEvmOperationsUtil.addressCheckExecution(
                frame,
                () -> frame.getStackItem(0),
                () -> cost(frame),
                () -> super.execute(frame, evm),
                addressValidator);
    }
}
