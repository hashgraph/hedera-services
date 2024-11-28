/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

    /**
     * Opcode for emitting event logs: LOG0(memory[offset:offset+length]).
     */
    LOG0(0xA0),
    /**
     * Opcode for emitting event logs: LOG1(memory[offset:offset+length], topic0).
     */
    LOG1(0xA1),
    /**
     * Opcode for emitting event logs: LOG2(memory[offset:offset+length], topic0, topic1).
     */
    LOG2(0xA2),
    /**
     * Opcode for emitting event logs: LOG3(memory[offset:offset+length], topic0, topic1, topic2).
     */
    LOG3(0xA3),
    /**
     * Opcode for emitting event logs: LOG4(memory[offset:offset+length], topic0, topic1, topic2, topic3).
     */
    LOG4(0xA4),
    /**
     * Opcode that determines the current chain id.
     */
    CHAINID(0x46),
    /**
     * Opcode to create a child contract.
     */
    CREATE(0xf0),
    /**
     * Opcode to create a child contract with a deterministic address.
     */
    CREATE2(0xf5),
    /**
     * Please see <a href="https://eips.ethereum.org/EIPS/eip-4399">EIP-4399</a> for details on this
     * opcodes Ethereum semantics and implementation.
     */
    PREVRANDAO(0x44),
    /**
     * Pre Cancun hardfork this opcode is used to send all funds to an address
     * After Cancun, the opcode used to destroy the contract.
     */
    SELFDESTRUCT(0xff);

    CustomizedOpcodes(int opcode) {
        this.opcode = opcode;
    }

    /**
     * @return the opcode
     */
    public int opcode() {
        return opcode;
    }

    private final int opcode;
}
