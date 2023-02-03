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

import static org.junit.jupiter.api.Assertions.*;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvmRedirectForTokenPrecompileTest {

    public static final Bytes REDIRECT_INPUT =
            Bytes.fromHexString(
                    "0x618dc65e000000000000000000000000000000000000000000000000000000000000043c0000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000002470a08231000000000000000000000000000000000000000000000000000000000000043b00000000000000000000000000000000000000000000000000000000");

    @Test
    void decodesExplicitRedirectInput() {
        final var decodedInput =
                EvmRedirectForTokenPrecompile.decodeExplicitRedirectForToken(REDIRECT_INPUT);

        assertTrue(decodedInput.token().length > 0);
        assertTrue(decodedInput.data().length > 0);
    }
}
