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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.allowance;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.APPROVED_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OWNER_HEADLONG_ADDRESS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.allowance.GetAllowanceCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.allowance.GetAllowanceTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.ClassicTransfersTranslator;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GetAllowanceTranslatorTest {
    @Mock
    private HtsCallAttempt attempt;

    @Mock
    private HederaWorldUpdater.Enhancement enhancement;

    private GetAllowanceTranslator subject;

    @BeforeEach
    void setUp() {
        subject = new GetAllowanceTranslator();
    }

    @Test
    void matchesGetAllowance() {
        given(attempt.selector()).willReturn(GetAllowanceTranslator.GET_ALLOWANCE.selector());
        final var matches = subject.matches(attempt);
        assertTrue(matches);
    }

    @Test
    void matchesERCGetAllowance() {
        given(attempt.selector()).willReturn(GetAllowanceTranslator.GET_ALLOWANCE.selector());
        final var matches = subject.matches(attempt);
        assertTrue(matches);
    }

    @Test
    void failsOnInvalidSelector() {
        given(attempt.selector()).willReturn(ClassicTransfersTranslator.CRYPTO_TRANSFER.selector());
        final var matches = subject.matches(attempt);
        assertFalse(matches);
    }

    @Test
    void callFromErcGetApprovedTest() {
        final Bytes inputBytes = Bytes.wrapByteBuffer(GetAllowanceTranslator.ERC_GET_ALLOWANCE.encodeCall(
                Tuple.of(OWNER_HEADLONG_ADDRESS, APPROVED_HEADLONG_ADDRESS)));
        given(attempt.selector()).willReturn(GetAllowanceTranslator.ERC_GET_ALLOWANCE.selector());
        given(attempt.inputBytes()).willReturn(inputBytes.toArray());
        given(attempt.enhancement()).willReturn(enhancement);

        final var call = subject.callFrom(attempt);
        assertThat(call).isInstanceOf(GetAllowanceCall.class);
    }

    @Test
    void callFromGetAllowanceTest() {
        final Bytes inputBytes = Bytes.wrapByteBuffer(GetAllowanceTranslator.GET_ALLOWANCE.encodeCall(
                Tuple.of(FUNGIBLE_TOKEN_HEADLONG_ADDRESS, OWNER_HEADLONG_ADDRESS, APPROVED_HEADLONG_ADDRESS)));
        given(attempt.selector()).willReturn(GetAllowanceTranslator.GET_ALLOWANCE.selector());
        given(attempt.inputBytes()).willReturn(inputBytes.toArray());
        given(attempt.enhancement()).willReturn(enhancement);

        final var call = subject.callFrom(attempt);
        assertThat(call).isInstanceOf(GetAllowanceCall.class);
    }
}
