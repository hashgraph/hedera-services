/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.updatetokencustomfees;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.updatetokencustomfees.UpdateTokenCustomFeesDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.updatetokencustomfees.UpdateTokenCustomFeesTranslator;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.common.CallTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateTokenCustomFeesTranslatorTest extends CallTestBase {

    @Mock
    private HtsCallAttempt attempt;

    @Mock
    private UpdateTokenCustomFeesDecoder decoder;

    private UpdateTokenCustomFeesTranslator subject;

    @BeforeEach
    void setUp() {
        subject = new UpdateTokenCustomFeesTranslator(decoder);
    }

    @Test
    void matchesUpdateFungibleTokenCustomFees() {
        // given:
        given(attempt.selector())
                .willReturn(UpdateTokenCustomFeesTranslator.UPDATE_FUNGIBLE_TOKEN_CUSTOM_FEES_FUNCTION.selector());
        // expect:
        assertTrue(subject.matches(attempt));
    }

    @Test
    void matchesUpdateNonFungibleTokenCustomFees() {
        // given:
        given(attempt.selector())
                .willReturn(UpdateTokenCustomFeesTranslator.UPDATE_NON_FUNGIBLE_TOKEN_CUSTOM_FEES_FUNCTION.selector());
        // expect:
        assertTrue(subject.matches(attempt));
    }
}
