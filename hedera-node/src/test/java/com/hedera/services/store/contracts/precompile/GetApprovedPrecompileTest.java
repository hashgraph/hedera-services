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

import static com.hedera.services.store.contracts.precompile.impl.GetApprovedPrecompile.decodeGetApproved;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetApprovedPrecompileTest {
    private static final Bytes GET_APPROVED_INPUT_ERC =
            Bytes.fromHexString(
                    "0x081812fc0000000000000000000000000000000000000000000000000000000000000001");
    private static final Bytes GET_APPROVED_LONG_OVERFLOWN =
            Bytes.fromHexString(
                    "0x081812fc0000000000000000000000000000000000000000000000010000000000000001");
    private static final Bytes GET_APPROVED_INPUT_HAPI =
            Bytes.fromHexString(
                    "0x098f236600000000000000000000000000000000000000000000000000000000000012340000000000000000000000000000000000000000000000000000000000000001");

    private static final long TOKEN_NUM_HAPI_TOKEN = 0x1234;

    private static final TokenID TOKEN_ID =
            TokenID.newBuilder().setTokenNum(TOKEN_NUM_HAPI_TOKEN).build();

    @Test
    void decodeGetApprovedInputERC() {
        final var decodedInput = decodeGetApproved(GET_APPROVED_INPUT_ERC, TOKEN_ID);

        assertEquals(TOKEN_ID.getTokenNum(), decodedInput.tokenId().getTokenNum());
        assertEquals(1, decodedInput.serialNo());
    }

    @Test
    void decodeGetApprovedShouldThrowOnSerialNoOverflown() {
        assertThrows(
                ArithmeticException.class,
                () -> decodeGetApproved(GET_APPROVED_LONG_OVERFLOWN, TOKEN_ID));
    }

    @Test
    void decodeGetApprovedInput() {
        final var decodedInput = decodeGetApproved(GET_APPROVED_INPUT_HAPI, null);

        assertEquals(TOKEN_NUM_HAPI_TOKEN, decodedInput.tokenId().getTokenNum());
        assertEquals(1, decodedInput.serialNo());
    }
}
