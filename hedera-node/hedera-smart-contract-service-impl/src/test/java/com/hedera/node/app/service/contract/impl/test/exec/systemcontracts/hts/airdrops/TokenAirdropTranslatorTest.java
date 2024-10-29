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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.airdrops;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.airdrops.TokenAirdropTranslator.TOKEN_AIRDROP;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SENDER_ID;
import static com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.CallAttemptHelpers.prepareHtsAttemptWithSelectorAndCustomConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.airdrops.TokenAirdropDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.airdrops.TokenAirdropTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.mint.MintTranslator;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.common.CallTestBase;
import com.hedera.node.config.data.ContractsConfig;
import com.swirlds.config.api.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenAirdropTranslatorTest extends CallTestBase {

    @Mock
    private TokenAirdropDecoder decoder;

    @Mock
    private HtsCallAttempt attempt;

    @Mock
    private ContractsConfig contractsConfig;

    @Mock
    private Configuration configuration;

    @Mock
    private SystemContractGasCalculator gasCalculator;

    @Mock
    private TransactionBody transactionBody;

    @Mock
    private HederaWorldUpdater.Enhancement enhancement;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private VerificationStrategy verificationStrategy;

    @Mock
    private VerificationStrategies verificationStrategies;

    @Mock
    private AccountID payerId;

    private TokenAirdropTranslator translator;

    @BeforeEach
    void setUp() {
        translator = new TokenAirdropTranslator(decoder);
    }

    @Test
    void matchesWhenAirdropEnabled() {
        when(configuration.getConfigData(ContractsConfig.class)).thenReturn(contractsConfig);
        when(contractsConfig.systemContractAirdropTokensEnabled()).thenReturn(true);
        attempt = prepareHtsAttemptWithSelectorAndCustomConfig(
                TOKEN_AIRDROP,
                translator,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                configuration);

        boolean result = translator.matches(attempt);

        assertTrue(result);
    }

    @Test
    void matchesWhenAirdropDisabled() {
        when(configuration.getConfigData(ContractsConfig.class)).thenReturn(contractsConfig);
        when(contractsConfig.systemContractAirdropTokensEnabled()).thenReturn(false);
        attempt = prepareHtsAttemptWithSelectorAndCustomConfig(
                TOKEN_AIRDROP,
                translator,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                configuration);

        boolean result = translator.matches(attempt);

        assertFalse(result);
    }

    @Test
    void matchesFailsForRandomSelector() {
        when(configuration.getConfigData(ContractsConfig.class)).thenReturn(contractsConfig);
        when(contractsConfig.systemContractAirdropTokensEnabled()).thenReturn(true);
        attempt = prepareHtsAttemptWithSelectorAndCustomConfig(
                MintTranslator.MINT,
                translator,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                configuration);

        boolean result = translator.matches(attempt);

        assertFalse(result);
    }

    @Test
    void gasRequirementCalculatesCorrectly() {
        long expectedGas = 1000L;
        when(gasCalculator.gasRequirement(transactionBody, DispatchType.TOKEN_AIRDROP, payerId))
                .thenReturn(expectedGas);

        long result = TokenAirdropTranslator.gasRequirement(transactionBody, gasCalculator, enhancement, payerId);

        assertEquals(expectedGas, result);
    }

    @Test
    void callFromCreatesCorrectCall() {
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(addressIdConverter.convertSender(any())).willReturn(SENDER_ID);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);

        final var call = translator.callFrom(attempt);
        assertEquals(DispatchForResponseCodeHtsCall.class, call.getClass());
        verify(decoder).decodeAirdrop(attempt);
    }
}
