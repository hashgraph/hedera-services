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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hss.scheduledcreate;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.scheduledcreate.ScheduledCreateTranslator.SCHEDULED_CREATE_FUNGIBLE;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.scheduledcreate.ScheduledCreateTranslator.SCHEDULED_CREATE_NON_FUNGIBLE;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.signschedule.SignScheduleTranslator.SIGN_SCHEDULE;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.DEFAULT_CONFIG;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SENDER_ID;
import static com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.CallAttemptHelpers.prepareHssAttemptWithSelectorAndCustomConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.HssCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.scheduledcreate.ScheduledCreateCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.scheduledcreate.ScheduledCreateDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.scheduledcreate.ScheduledCreateTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.common.CallTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledCreateTranslatorTest extends CallTestBase {

    @Mock
    private ScheduledCreateDecoder decoder;

    @Mock
    private SystemContractGasCalculator gasCalculator;

    @Mock
    private TransactionBody transactionBody;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private VerificationStrategy verificationStrategy;

    @Mock
    private VerificationStrategies verificationStrategies;

    @Mock
    private AccountID payerId;

    @Mock
    private HssCallAttempt attempt;

    private ScheduledCreateTranslator subject;

    @BeforeEach
    void setUp() {
        subject = new ScheduledCreateTranslator(decoder);
    }

    @Test
    void matchesScheduleCreateFungible() {
        // given
        final var attempt = prepareHssAttemptWithSelectorAndCustomConfig(
                SCHEDULED_CREATE_FUNGIBLE,
                subject,
                mockEnhancement(),
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                DEFAULT_CONFIG);

        // when/then
        assertTrue(subject.matches(attempt));
    }

    @Test
    void matchesScheduleCreateNonFungible() {
        // given
        final var attempt = prepareHssAttemptWithSelectorAndCustomConfig(
                SCHEDULED_CREATE_NON_FUNGIBLE,
                subject,
                mockEnhancement(),
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                DEFAULT_CONFIG);

        // when/then
        assertTrue(subject.matches(attempt));
    }

    @Test
    void matchesFailsForOtherSelector() {
        // given
        final var attempt = prepareHssAttemptWithSelectorAndCustomConfig(
                SIGN_SCHEDULE,
                subject,
                mockEnhancement(),
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                DEFAULT_CONFIG);

        // when/then
        assertFalse(subject.matches(attempt));
    }

    @Test
    void calculatesGasRequirementCorrectly() {
        // given
        final var expectedGas = 1234L;

        given(gasCalculator.gasRequirement(transactionBody, DispatchType.SCHEDULE_CREATE, payerId))
                .willReturn(expectedGas);

        // when
        final var actualGas =
                ScheduledCreateTranslator.gasRequirement(transactionBody, gasCalculator, mockEnhancement(), payerId);

        // then
        assertEquals(expectedGas, actualGas);
    }

    @Test
    void createsCallFromFTAttempt() {

        // given
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(attempt.senderId()).willReturn(SENDER_ID);
        given(attempt.isSelector(SCHEDULED_CREATE_FUNGIBLE)).willReturn(true);
        given(decoder.decodeScheduledCreateFT(any())).willReturn(transactionBody);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);

        // when
        final var call = subject.callFrom(attempt);

        // then
        assertNotNull(call);
        assertInstanceOf(ScheduledCreateCall.class, call);
        verify(decoder).decodeScheduledCreateFT(any());
    }

    @Test
    void createsCallFromNFTAttempt() {

        // given
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(attempt.senderId()).willReturn(SENDER_ID);
        given(attempt.isSelector(SCHEDULED_CREATE_NON_FUNGIBLE)).willReturn(true);
        given(attempt.isSelector(SCHEDULED_CREATE_FUNGIBLE)).willReturn(false);
        given(decoder.decodeScheduledCreateNFT(any())).willReturn(transactionBody);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);

        // when
        final var call = subject.callFrom(attempt);

        // then
        assertNotNull(call);
        assertInstanceOf(ScheduledCreateCall.class, call);
        verify(decoder).decodeScheduledCreateNFT(any());
    }
}
