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
package com.hedera.services.ledger;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HederaLedgerTokenXfersTest extends BaseHederaLedgerTestHelper {
    @BeforeEach
    void setup() {
        commonSetup();
        setupWithMockLedger();
    }

    @Test
    void tokenTransferHappyPathWOrks() {
        // setup
        given(subject.adjustTokenBalance(misc, tokenId, -1_000)).willReturn(OK);
        given(subject.adjustTokenBalance(rand, tokenId, 1_000)).willReturn(OK);

        // when:
        var outcome = subject.doTokenTransfer(tokenId, misc, rand, 1_000);

        // then:
        assertEquals(OK, outcome);
        verify(tokenStore, never()).exists(tokenId);
    }
}
