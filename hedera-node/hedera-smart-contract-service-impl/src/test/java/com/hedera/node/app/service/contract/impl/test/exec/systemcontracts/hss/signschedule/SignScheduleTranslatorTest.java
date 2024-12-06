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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hss.signschedule;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.APPROVED_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_SYSTEM_LONG_ZERO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OWNER_BESU_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SOMEBODY;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.bytesForRedirectScheduleTxn;
import static com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.CallAttemptHelpers.prepareHssAttemptWithBytesAndCustomConfig;
import static com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.CallAttemptHelpers.prepareHssAttemptWithSelectorAndCustomConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.SystemContractOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.DispatchForResponseCodeHssCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.HssCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.signschedule.SignScheduleTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.mint.MintTranslator;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.config.data.ContractsConfig;
import com.swirlds.config.api.Configuration;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SignScheduleTranslatorTest {

    @Mock
    private HssCallAttempt attempt;

    @Mock
    private Configuration configuration;

    @Mock
    private ContractsConfig contractsConfig;

    @Mock
    private HederaWorldUpdater.Enhancement enhancement;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private VerificationStrategies verificationStrategies;

    @Mock
    private VerificationStrategy verificationStrategy;

    @Mock
    private SystemContractGasCalculator gasCalculator;

    @Mock
    private HederaNativeOperations nativeOperations;

    @Mock
    private SystemContractOperations systemContractOperations;

    @Mock
    private TransactionBody transactionBody;

    @Mock
    private AccountID payerId;

    @Mock
    private Schedule schedule;

    @Mock
    private ScheduleID scheduleID;

    private SignScheduleTranslator subject;

    @BeforeEach
    void setUp() {
        subject = new SignScheduleTranslator();
    }

    @Test
    void testMatchesWhenSignScheduleEnabled() {
        // given:
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.systemContractSignScheduleEnabled()).willReturn(true);
        attempt = prepareHssAttemptWithSelectorAndCustomConfig(
                SignScheduleTranslator.SIGN_SCHEDULE_PROXY,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                configuration);

        // when:
        boolean matches = subject.matches(attempt);

        // then:
        assertTrue(matches);
    }

    @Test
    void testFailsMatchesWhenSignScheduleEnabled() {
        // given:
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.systemContractSignScheduleEnabled()).willReturn(false);
        attempt = prepareHssAttemptWithSelectorAndCustomConfig(
                SignScheduleTranslator.SIGN_SCHEDULE_PROXY,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                configuration);

        // when:
        boolean matches = subject.matches(attempt);

        // then:
        assertFalse(matches);
    }

    @Test
    void testMatchesWhenAuthorizeScheduleEnabled() {
        // given:
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.systemContractAuthorizeScheduleEnabled()).willReturn(true);
        attempt = prepareHssAttemptWithSelectorAndCustomConfig(
                SignScheduleTranslator.AUTHORIZE_SCHEDULE,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                configuration);

        // when:
        boolean matches = subject.matches(attempt);

        // then:
        assertTrue(matches);
    }

    @Test
    void testFailsMatchesWhenAuthorizeScheduleEnabled() {
        // given:
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.systemContractAuthorizeScheduleEnabled()).willReturn(false);
        attempt = prepareHssAttemptWithSelectorAndCustomConfig(
                SignScheduleTranslator.AUTHORIZE_SCHEDULE,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                configuration);

        // when:
        boolean matches = subject.matches(attempt);

        // then:
        assertFalse(matches);
    }

    @Test
    void testMatchesFailsOnRandomSelector() {
        // given:
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.systemContractSignScheduleEnabled()).willReturn(true);
        attempt = prepareHssAttemptWithSelectorAndCustomConfig(
                MintTranslator.MINT,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                configuration);

        // when:
        boolean matches = subject.matches(attempt);

        // then:
        assertFalse(matches);
    }

    @Test
    void testScheduleIdForSignScheduleProxy() {
        given(enhancement.nativeOperations()).willReturn(nativeOperations);
        given(enhancement.systemOperations()).willReturn(systemContractOperations);
        given(nativeOperations.getSchedule(anyLong())).willReturn(schedule);
        given(schedule.scheduleId()).willReturn(scheduleID);
        given(nativeOperations.getAccount(payerId)).willReturn(SOMEBODY);
        given(addressIdConverter.convertSender(OWNER_BESU_ADDRESS)).willReturn(payerId);
        given(verificationStrategies.activatingOnlyContractKeysFor(OWNER_BESU_ADDRESS, false, nativeOperations))
                .willReturn(verificationStrategy);

        // when:
        attempt = prepareHssAttemptWithBytesAndCustomConfig(
                bytesForRedirectScheduleTxn(
                        SignScheduleTranslator.SIGN_SCHEDULE_PROXY.selector(), NON_SYSTEM_LONG_ZERO_ADDRESS),
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                configuration);

        // then:
        final var call = subject.callFrom(attempt);

        assertThat(call).isInstanceOf(DispatchForResponseCodeHssCall.class);
    }

    @Test
    void testScheduleIdForAuthorizeScheduleProxy() {
        given(enhancement.nativeOperations()).willReturn(nativeOperations);
        given(enhancement.systemOperations()).willReturn(systemContractOperations);
        given(nativeOperations.getSchedule(anyLong())).willReturn(schedule);
        given(nativeOperations.getAccount(payerId)).willReturn(SOMEBODY);
        given(schedule.scheduleId()).willReturn(scheduleID);
        given(addressIdConverter.convertSender(OWNER_BESU_ADDRESS)).willReturn(payerId);
        given(verificationStrategies.activatingOnlyContractKeysFor(OWNER_BESU_ADDRESS, false, nativeOperations))
                .willReturn(verificationStrategy);

        // when:
        final var input = Bytes.wrapByteBuffer(
                SignScheduleTranslator.AUTHORIZE_SCHEDULE.encodeCall(Tuple.of(APPROVED_HEADLONG_ADDRESS)));
        attempt = prepareHssAttemptWithBytesAndCustomConfig(
                input, subject, enhancement, addressIdConverter, verificationStrategies, gasCalculator, configuration);

        // then:
        final var call = subject.callFrom(attempt);

        assertThat(call).isInstanceOf(DispatchForResponseCodeHssCall.class);
    }

    @Test
    void testGasRequirement() {
        long expectedGas = 1000L;
        when(gasCalculator.gasRequirement(transactionBody, DispatchType.SCHEDULE_SIGN, payerId))
                .thenReturn(expectedGas);

        long gas = SignScheduleTranslator.gasRequirement(transactionBody, gasCalculator, enhancement, payerId);

        assertEquals(expectedGas, gas);
    }
}
