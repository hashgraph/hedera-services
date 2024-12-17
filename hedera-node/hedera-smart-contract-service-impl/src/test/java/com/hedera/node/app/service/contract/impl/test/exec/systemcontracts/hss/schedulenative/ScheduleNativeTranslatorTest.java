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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hss.schedulenative;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.schedulenative.ScheduleNativeTranslator.SCHEDULED_NATIVE_CALL;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.signschedule.SignScheduleTranslator.SIGN_SCHEDULE;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.DEFAULT_CONFIG;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.HTS_SYSTEM_CONTRACT_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SENDER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asHeadlongAddress;
import static com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.CallAttemptHelpers.prepareHssAttemptWithBytesAndCustomConfig;
import static com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.CallAttemptHelpers.prepareHssAttemptWithSelectorAndCustomConfig;
import static com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.create.CreateTestHelper.CREATE_FUNGIBLE_V3_TUPLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.HssCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.schedulenative.ScheduleNativeCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.schedulenative.ScheduleNativeTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallFactory;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateTranslator;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.common.CallTestBase;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleNativeTranslatorTest extends CallTestBase {

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

    @Mock
    private HtsCallFactory htsCallFactory;

    // Mock to populate the selectors map
    @Mock
    private CreateDecoder decoder;

    private CreateTranslator createTranslator;

    private ScheduleNativeTranslator subject;

    @BeforeEach
    void setUp() {
        createTranslator = new CreateTranslator(decoder);
        subject = new ScheduleNativeTranslator(htsCallFactory);
    }

    @Test
    void matchesScheduleCreate() {
        // given
        final var attempt = prepareHssAttemptWithBytesAndCustomConfig(
                Bytes.wrapByteBuffer(SCHEDULED_NATIVE_CALL.encodeCallWithArgs(
                        asHeadlongAddress(HTS_SYSTEM_CONTRACT_ADDRESS),
                        CreateTranslator.CREATE_FUNGIBLE_TOKEN_V3
                                .encodeCall(CREATE_FUNGIBLE_V3_TUPLE)
                                .array(),
                        asHeadlongAddress(SENDER_ID.accountNum()))),
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
                ScheduleNativeTranslator.gasRequirement(transactionBody, gasCalculator, mockEnhancement(), payerId);

        // then
        assertEquals(expectedGas, actualGas);
    }

    @Test
    void createsCall() {

        // given
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(attempt.senderId()).willReturn(SENDER_ID);
        given(attempt.isSelector(SCHEDULED_NATIVE_CALL)).willReturn(true);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);

        // when
        final var call = subject.callFrom(attempt);

        // then
        assertNotNull(call);
        assertInstanceOf(ScheduleNativeCall.class, call);
    }
}
