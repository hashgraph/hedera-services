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

import static com.hedera.services.store.contracts.precompile.impl.SetApprovalForAllPrecompile.decodeSetApprovalForAll;
import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SetApprovalForAllPrecompileTest {
    public static final Bytes SET_APPROVAL_FOR_ALL_INPUT_ERC =
            Bytes.fromHexString(
                    "0xa22cb46500000000000000000000000000000000000000000000000000000000000006400000000000000000000000000000000000000000000000000000000000000001");

    public static final Bytes SET_APPROVAL_FOR_ALL_INPUT_HAPI =
            Bytes.fromHexString(
                    "0x367605ca000000000000000000000000000000000000000000000000000000000000123400000000000000000000000000000000000000000000000000000000000006400000000000000000000000000000000000000000000000000000000000000001");
    private static final long TOKEN_NUM_HAPI_TOKEN = 0x1234;
    private static final long ACCOUNT_NUM_APPROVE_ALL_TO = 0x640;
    private static final TokenID TOKEN_ID =
            TokenID.newBuilder().setTokenNum(TOKEN_NUM_HAPI_TOKEN).build();

    @Test
    void decodeSetApprovalForAllERC() {
        final var decodedInput =
                decodeSetApprovalForAll(SET_APPROVAL_FOR_ALL_INPUT_ERC, TOKEN_ID, identity());

        assertEquals(TOKEN_ID.getTokenNum(), decodedInput.tokenId().getTokenNum());
        assertEquals(ACCOUNT_NUM_APPROVE_ALL_TO, decodedInput.to().getAccountNum());
        assertTrue(decodedInput.approved());
    }

    @Test
    void decodeSetApprovalForAllHAPI() {
        final var decodedInput =
                decodeSetApprovalForAll(SET_APPROVAL_FOR_ALL_INPUT_HAPI, null, identity());

        assertEquals(TOKEN_NUM_HAPI_TOKEN, decodedInput.tokenId().getTokenNum());
        assertEquals(ACCOUNT_NUM_APPROVE_ALL_TO, decodedInput.to().getAccountNum());
        assertTrue(decodedInput.approved());
    }
}
