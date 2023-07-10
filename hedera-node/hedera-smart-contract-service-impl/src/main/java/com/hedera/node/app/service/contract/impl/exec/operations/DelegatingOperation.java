package com.hedera.node.app.service.contract.impl.exec.operations;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;

public class DelegatingOperation implements Operation {
    private final Operation delegate;

    public DelegatingOperation(@NonNull final Operation delegate) {
        this.delegate = delegate;
    }

    @Override
    public OperationResult execute(MessageFrame frame, EVM evm) {
        return null;
    }

    @Override
    public int getOpcode() {
        return 0;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public int getStackItemsConsumed() {
        return 0;
    }

    @Override
    public int getStackItemsProduced() {
        return 0;
    }
}
