/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.impl.TokenURIPrecompile.decodeTokenUriNFT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenURIPrecompileTest {

    private static final Bytes TOKEN_URI_INPUT =
            Bytes.fromHexString(
                    "0xc87b56dd0000000000000000000000000000000000000000000000000000000000000001");
    private static final Bytes TOKEN_URI_LONG_OVERFLOWN =
            Bytes.fromHexString(
                    "0xc87b56dd0000000000000000000000000000000000000000000000010000000000000001");

    @Test
    void decodeTokenUriInput() {
        final var decodedInput = decodeTokenUriNFT(TOKEN_URI_INPUT);

        assertEquals(1, decodedInput.serialNo());
    }

    @Test
    void decodeTokenUriShouldThrowOnTokenIdOverflown() {
        assertThrows(ArithmeticException.class, () -> decodeTokenUriNFT(TOKEN_URI_LONG_OVERFLOWN));
    }
}
