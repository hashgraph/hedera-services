/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.mint;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.mint.MintDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.mint.MintTranslator;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MintDecoderTest {
    @Mock
    private HtsCallAttempt attempt;

    private final MintDecoder subject = new MintDecoder();

    @Test
    void mintWorks() {
        final var encoded = MintTranslator.MINT
                .encodeCallWithArgs(FUNGIBLE_TOKEN_HEADLONG_ADDRESS, BigInteger.valueOf(VALUE), new byte[][] {})
                .array();
        given(attempt.inputBytes()).willReturn(encoded);
        final var mint = subject.decodeMint(attempt).tokenMintOrThrow();
        assertEquals(FUNGIBLE_TOKEN_ID, mint.token());
        assertEquals(VALUE, mint.amount());
    }

    @Test
    void mintV2Works() {
        final var encoded = MintTranslator.MINT_V2
                .encodeCallWithArgs(FUNGIBLE_TOKEN_HEADLONG_ADDRESS, VALUE, new byte[][] {})
                .array();
        given(attempt.inputBytes()).willReturn(encoded);
        final var mint = subject.decodeMintV2(attempt).tokenMintOrThrow();
        assertEquals(FUNGIBLE_TOKEN_ID, mint.token());
        assertEquals(VALUE, mint.amount());
    }
}
