package com.hedera.node.app.service.contract.impl.exec.operations;

/**
 * A few opcodes that need more-than-usual customization in the Hedera EVM and thus
 * don't even inherit the corresponding Besu operation, instead directly overriding
 * {@link org.hyperledger.besu.evm.operation.AbstractOperation} themselves.
 *
 * <p>Please see <a href="https://ethereum.org/en/developers/docs/evm/opcodes/">here</a>
 * for a review of the EVM opcodes.
 */
public enum CustomizedOpcodes {
    CHAINID(0x46), CREATE(0xf0), CREATE2(0xf5);

    CustomizedOpcodes(int opcode) {
        this.opcode = opcode;
    }

    public int opcode() {
        return opcode;
    }

    private final int opcode;
}
