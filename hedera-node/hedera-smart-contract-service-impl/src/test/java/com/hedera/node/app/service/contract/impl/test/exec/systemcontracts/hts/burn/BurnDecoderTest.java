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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.burn;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.burn.BurnDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.burn.BurnTranslator;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.common.CallTestBase;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * Unit tests for burn decoder
 */
public class BurnDecoderTest extends CallTestBase {

    @Mock
    private HtsCallAttempt attempt;

    private BurnDecoder subject = new BurnDecoder();

    @Test
    void burnTokenHappyPathV1() {
        final var encoded = BurnTranslator.BURN_TOKEN_V1
                .encodeCallWithArgs(FUNGIBLE_TOKEN_HEADLONG_ADDRESS, BigInteger.valueOf(VALUE), new long[] {})
                .array();
        given(attempt.inputBytes()).willReturn(encoded);
        final var burn = subject.decodeBurn(attempt).tokenBurnOrThrow();
        assertEquals(FUNGIBLE_TOKEN_ID, burn.token());
        assertEquals(VALUE, burn.amount());
    }

    @Test
    void burnTokenHappyPathV2() {
        final var encoded = BurnTranslator.BURN_TOKEN_V2
                .encodeCallWithArgs(FUNGIBLE_TOKEN_HEADLONG_ADDRESS, VALUE, new long[] {})
                .array();
        given(attempt.inputBytes()).willReturn(encoded);
        final var burn = subject.decodeBurnV2(attempt).tokenBurnOrThrow();
        assertEquals(FUNGIBLE_TOKEN_ID, burn.token());
        assertEquals(VALUE, burn.amount());
    }
}
