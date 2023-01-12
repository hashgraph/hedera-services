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
package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

class EvmKeyTest {

    @Test
    void test() {
        EvmKey evmKey =
                new EvmKey(
                        null,
                        new byte[] {
                            -120, -12, 112, 11, 85, 25, -66, 76, -83, -44, 11, -40, 28, -44, -43,
                            -30, 46, 60, -5, 88, 6, 49, 52, -114, 115, -26, 85, -87, -54, 53, -118,
                            -116
                        },
                        new byte[0],
                        null);

        EvmKey evmKey2 = new EvmKey();

        assertEquals(Address.ZERO, evmKey.getContractId());
        assertEquals(Address.ZERO, evmKey.getDelegatableContractId());
    }
}
