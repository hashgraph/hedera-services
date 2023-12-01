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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.wipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.wipe.WipeDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.wipe.WipeTranslator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class WipeTranslatorTest {

    @Mock
    private HtsCallAttempt attempt;

    private final WipeDecoder decoder = new WipeDecoder();

    private WipeTranslator subject;

    @BeforeEach
    void setUp() {
        subject = new WipeTranslator(decoder);
    }

    @Test
    void matchesWipeFungibleV1Test() {
        given(attempt.selector()).willReturn(WipeTranslator.WIPE_FUNGIBLE_V1.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

    @Test
    void matchesWipeFungibleV2Test() {
        given(attempt.selector()).willReturn(WipeTranslator.WIPE_FUNGIBLE_V2.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

    @Test
    void matchesWipeNftTest() {
        given(attempt.selector()).willReturn(WipeTranslator.WIPE_NFT.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }
}
