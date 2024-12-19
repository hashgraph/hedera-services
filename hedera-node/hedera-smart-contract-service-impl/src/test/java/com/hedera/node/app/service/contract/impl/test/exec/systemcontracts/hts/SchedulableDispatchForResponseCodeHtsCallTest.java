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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SENDER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall.OutputFn;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.SchedulableDispatchForResponseCodeHtsCall;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.common.CallTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchedulableDispatchForResponseCodeHtsCallTest extends CallTestBase {

    @Mock
    private SystemContractGasCalculator gasCalculator;

    @Mock
    private VerificationStrategy verificationStrategy;

    @Mock
    private DispatchGasCalculator dispatchGasCalculator;

    @Mock
    private DispatchForResponseCodeHtsCall.FailureCustomizer failureCustomizer;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private HtsCallAttempt attempt;

    private SchedulableTransactionBody schedulableTransactionBody;
    private TransactionBody transactionBody;
    private SchedulableDispatchForResponseCodeHtsCall subject;

    @BeforeEach
    void setUp() {
        final var tokenCreation = TokenCreateTransactionBody.newBuilder()
                .symbol("TEST")
                .name("Test Token")
                .treasury(SENDER_ID)
                .build();
        schedulableTransactionBody = SchedulableTransactionBody.newBuilder()
                .tokenCreation(tokenCreation)
                .build();

        transactionBody =
                TransactionBody.newBuilder().tokenCreation(tokenCreation).build();
    }

    @Test
    void constructsWithFullConfiguration() {
        // given/when
        subject = new SchedulableDispatchForResponseCodeHtsCall(
                mockEnhancement(),
                gasCalculator,
                SENDER_ID,
                transactionBody,
                verificationStrategy,
                dispatchGasCalculator,
                failureCustomizer,
                OutputFn.STANDARD_OUTPUT_FN,
                schedulableTransactionBody);

        // then
        assertNotNull(subject);
        assertEquals(schedulableTransactionBody, subject.asSchedulableDispatchIn());
    }

    @Test
    void constructsWithMinimalConfiguration() {
        // given/when
        mockAttempt();
        subject = new SchedulableDispatchForResponseCodeHtsCall(
                attempt, transactionBody, dispatchGasCalculator, schedulableTransactionBody);

        // then
        assertNotNull(subject);
        assertEquals(schedulableTransactionBody, subject.asSchedulableDispatchIn());
    }

    @Test
    void constructsWithFailureCustomizer() {
        // given/when
        mockAttempt();
        subject = new SchedulableDispatchForResponseCodeHtsCall(
                attempt, transactionBody, dispatchGasCalculator, failureCustomizer, schedulableTransactionBody);

        // then
        assertNotNull(subject);
        assertEquals(schedulableTransactionBody, subject.asSchedulableDispatchIn());
    }

    @Test
    void constructsWithOutputFunction() {
        // given/when
        mockAttempt();
        subject = new SchedulableDispatchForResponseCodeHtsCall(
                attempt,
                transactionBody,
                dispatchGasCalculator,
                OutputFn.STANDARD_OUTPUT_FN,
                schedulableTransactionBody);

        // then
        assertNotNull(subject);
        assertEquals(schedulableTransactionBody, subject.asSchedulableDispatchIn());
    }

    @Test
    void returnsSchedulableTransactionBody() {
        // given
        mockAttempt();
        subject = new SchedulableDispatchForResponseCodeHtsCall(
                attempt, transactionBody, dispatchGasCalculator, schedulableTransactionBody);

        // when
        final var result = subject.asSchedulableDispatchIn();

        // then
        assertEquals(schedulableTransactionBody, result);
    }

    private void mockAttempt() {
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(addressIdConverter.convertSender(any())).willReturn(SENDER_ID);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);
    }
}
