/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.evm.accounts;

import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaEvmContractAliasesTest {

    private final MockedHederaEvmContractAliases hederaEvmContractAliases = new MockedHederaEvmContractAliases();
    byte[] byteArray = new byte[20];

    @Test
    void non20ByteStringCannotBeMirror() {
        assertFalse(HederaEvmContractAliases.isMirror(new byte[] {(byte) 0xab, (byte) 0xcd}));
        assertFalse(HederaEvmContractAliases.isMirror(unhex("abcdefabcdefabcdefbabcdefabcdefabcdefbbbde")));
    }

    @Test
    void with20Byte() {
        assertTrue(HederaEvmContractAliases.isMirror(byteArray));
        assertTrue(
                hederaEvmContractAliases.isMirror(Address.fromHexString("0x000000000000000000000000000000000000071e")));
    }
}
