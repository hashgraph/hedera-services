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

package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvmGetTokenKeyPrecompileTest {

    public static final Bytes GET_TOKEN_KEY_INOUT = Bytes.fromHexString(
            "0x3c4dd32e00000000000000000000000000000000000000000000000000000000000003f40000000000000000000000000000000000000000000000000000000000000001");

    @Test
    void decodeGetTokenKey() {
        final var decodedInput = EvmGetTokenKeyPrecompile.decodeGetTokenKey(GET_TOKEN_KEY_INOUT);

        assertEquals(1, decodedInput.keyType());
        assertTrue(decodedInput.token().length > 0);
    }
}
