package com.hedera.node.app.service.contract.impl.exec.failure;

import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;

public enum CustomExceptionalHaltReason implements ExceptionalHaltReason {
    MISSING_ADDRESS("Invalid account reference");

    private final String description;

    CustomExceptionalHaltReason(final String description) {
        this.description = description;
    }

    @Override
    public String getDescription() {
        return description;
    }
}
