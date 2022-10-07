/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.store.contracts.precompile.impl.IsTokenPrecompile.decodeIsToken;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IsTokenPrecompileTest {
    private static final Bytes IS_TOKEN_INPUT =
            Bytes.fromHexString(
                    "0x19f373610000000000000000000000000000000000000000000000000000000000000b03");

    @Test
    void decodeIsTokenAsExpected() {
        final var decodedInput = decodeIsToken(IS_TOKEN_INPUT);
        assertEquals(TokenID.newBuilder().setTokenNum(2819).build(), decodedInput.tokenID());
        assertEquals(-1, decodedInput.serialNumber());
    }
}
