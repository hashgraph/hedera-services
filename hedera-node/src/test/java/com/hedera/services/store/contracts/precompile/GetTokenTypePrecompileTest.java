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

import static com.hedera.services.store.contracts.precompile.impl.GetTokenTypePrecompile.decodeGetTokenType;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.store.contracts.precompile.impl.GetTokenTypePrecompile;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetTokenTypePrecompileTest {
    private static final Bytes GET_TOKEN_TYPE_INPUT =
            Bytes.fromHexString(
                    "0x93272baf0000000000000000000000000000000000000000000000000000000000000b0d");
    private MockedStatic<GetTokenTypePrecompile> getTokenTypePrecompile;

    @BeforeEach
    void setUp() {
        getTokenTypePrecompile = Mockito.mockStatic(GetTokenTypePrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        getTokenTypePrecompile.close();
    }

    @Test
    void decodeGetTokenTypeAsExpected() {
        getTokenTypePrecompile
                .when(() -> decodeGetTokenType(GET_TOKEN_TYPE_INPUT))
                .thenCallRealMethod();
        final var decodedInput = decodeGetTokenType(GET_TOKEN_TYPE_INPUT);
        assertEquals(TokenID.newBuilder().setTokenNum(2829).build(), decodedInput.tokenID());
        assertEquals(-1, decodedInput.serialNumber());
    }
}
