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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.cancelairdrops;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SENDER_ID;
import static com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.CallAttemptHelpers.prepareHtsAttemptWithSelectorAndCustomConfig;
import static com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.CallAttemptHelpers.prepareHtsAttemptWithSelectorForRedirectWithConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.cancelairdrops.TokenCancelAirdropDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.cancelairdrops.TokenCancelAirdropTranslator;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater.Enhancement;
import com.hedera.node.config.data.ContractsConfig;
import com.swirlds.config.api.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenCancelAirdropTranslatorTest {

    @Mock
    private TokenCancelAirdropDecoder decoder;

    @Mock
    private HtsCallAttempt attempt;

    @Mock
    private ContractsConfig contractsConfig;

    @Mock
    private Configuration configuration;

    @Mock
    private Enhancement enhancement;

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
    private TransactionBody transactionBody;

    @Mock
    private AccountID payerId;

    private TokenCancelAirdropTranslator subject;

    @BeforeEach
    void setUp() {
        subject = new TokenCancelAirdropTranslator(decoder);
    }

    @Test
    void matchesHTSCancelAirdropEnabled() {
        // given:
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.systemContractCancelAirdropsEnabled()).willReturn(true);
        attempt = prepareHtsAttemptWithSelectorAndCustomConfig(
                TokenCancelAirdropTranslator.CANCEL_AIRDROP,
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
    void matchesHTSCancelAirdropDisabled() {
        // given:
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.systemContractCancelAirdropsEnabled()).willReturn(false);
        attempt = prepareHtsAttemptWithSelectorAndCustomConfig(
                TokenCancelAirdropTranslator.CANCEL_AIRDROP,
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
    void matchesHRCCancelFTAirdropEnabled() {
        // given:
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.systemContractCancelAirdropsEnabled()).willReturn(true);
        given(enhancement.nativeOperations()).willReturn(nativeOperations);
        attempt = prepareHtsAttemptWithSelectorForRedirectWithConfig(
                TokenCancelAirdropTranslator.HRC_CANCEL_AIRDROP_FT,
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
    void matchesHRCCancelAirdropDisabled() {
        // given:
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.systemContractCancelAirdropsEnabled()).willReturn(false);
        given(enhancement.nativeOperations()).willReturn(nativeOperations);
        attempt = prepareHtsAttemptWithSelectorForRedirectWithConfig(
                TokenCancelAirdropTranslator.HRC_CANCEL_AIRDROP_FT,
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
    void matchesHRCCancelNFTAirdropEnabled() {
        // given:
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.systemContractCancelAirdropsEnabled()).willReturn(true);
        given(enhancement.nativeOperations()).willReturn(nativeOperations);
        attempt = prepareHtsAttemptWithSelectorForRedirectWithConfig(
                TokenCancelAirdropTranslator.HRC_CANCEL_AIRDROP_NFT,
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
    void matchesHRCCancelNFTAirdropDisabled() {
        // given:
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.systemContractCancelAirdropsEnabled()).willReturn(false);
        given(enhancement.nativeOperations()).willReturn(nativeOperations);
        attempt = prepareHtsAttemptWithSelectorForRedirectWithConfig(
                TokenCancelAirdropTranslator.HRC_CANCEL_AIRDROP_NFT,
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
    void gasRequirementCalculatesCorrectly() {
        long expectedGas = 1000L;
        given(gasCalculator.gasRequirement(transactionBody, DispatchType.TOKEN_CANCEL_AIRDROP, payerId))
                .willReturn(expectedGas);

        long result = TokenCancelAirdropTranslator.gasRequirement(transactionBody, gasCalculator, enhancement, payerId);

        assertEquals(expectedGas, result);
    }

    @Test
    void callFromHtsCancelAirdrop() {
        // given:
        given(addressIdConverter.convertSender(any())).willReturn(SENDER_ID);
        given(verificationStrategies.activatingOnlyContractKeysFor(any(), anyBoolean(), any()))
                .willReturn(verificationStrategy);
        attempt = prepareHtsAttemptWithSelectorAndCustomConfig(
                TokenCancelAirdropTranslator.CANCEL_AIRDROP,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                configuration);

        // when:
        var call = subject.callFrom(attempt);

        // then:
        assertEquals(DispatchForResponseCodeHtsCall.class, call.getClass());
    }

    @Test
    void callFromHRCCancelFTAirdrop() {
        // given:
        given(addressIdConverter.convertSender(any())).willReturn(SENDER_ID);
        given(verificationStrategies.activatingOnlyContractKeysFor(any(), anyBoolean(), any()))
                .willReturn(verificationStrategy);
        attempt = prepareHtsAttemptWithSelectorAndCustomConfig(
                TokenCancelAirdropTranslator.HRC_CANCEL_AIRDROP_FT,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                configuration);

        // when:
        var call = subject.callFrom(attempt);

        // then:
        assertEquals(DispatchForResponseCodeHtsCall.class, call.getClass());
    }

    @Test
    void callFromHRCCancelNFTAirdrop() {
        // given:
        given(addressIdConverter.convertSender(any())).willReturn(SENDER_ID);
        given(verificationStrategies.activatingOnlyContractKeysFor(any(), anyBoolean(), any()))
                .willReturn(verificationStrategy);
        attempt = prepareHtsAttemptWithSelectorAndCustomConfig(
                TokenCancelAirdropTranslator.HRC_CANCEL_AIRDROP_NFT,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                configuration);

        // when:
        var call = subject.callFrom(attempt);

        // then:
        assertEquals(DispatchForResponseCodeHtsCall.class, call.getClass());
    }
}