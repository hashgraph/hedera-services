/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
    CHAINID(0x46),
    CREATE(0xf0),
    CREATE2(0xf5),
    SELFDESTRUCT(0xff);

    CustomizedOpcodes(int opcode) {
        this.opcode = opcode;
    }

    public int opcode() {
        return opcode;
    }

    private final int opcode;
}
