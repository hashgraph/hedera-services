/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.grantrevokekyc;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.burn.BurnTranslator.BURN_TOKEN_V2;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantrevokekyc.GrantRevokeKycTranslator.GRANT_KYC;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantrevokekyc.GrantRevokeKycTranslator.REVOKE_KYC;
import static com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.CallAttemptHelpers.prepareHtsAttemptWithSelector;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantrevokekyc.GrantRevokeKycDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantrevokekyc.GrantRevokeKycTranslator;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater.Enhancement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrantRevokeKycTranslatorTest {
    @Mock
    private HtsCallAttempt attempt;

    @Mock
    private SystemContractGasCalculator gasCalculator;

    @Mock
    private Enhancement enhancement;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private VerificationStrategies verificationStrategies;

    private final GrantRevokeKycDecoder decoder = new GrantRevokeKycDecoder();
    private GrantRevokeKycTranslator subject;

    @BeforeEach
    void setUp() {
        subject = new GrantRevokeKycTranslator(decoder);
    }

    @Test
    void matchesGrantKycTest() {
        attempt = prepareHtsAttemptWithSelector(
                GRANT_KYC, subject, enhancement, addressIdConverter, verificationStrategies, gasCalculator);
        assertTrue(subject.matches(attempt));
    }

    @Test
    void matchesRevokeKycTest() {
        attempt = prepareHtsAttemptWithSelector(
                REVOKE_KYC, subject, enhancement, addressIdConverter, verificationStrategies, gasCalculator);
        assertTrue(subject.matches(attempt));
    }

    @Test
    void matchesFailsWithIncorrectSelector() {
        attempt = prepareHtsAttemptWithSelector(
                BURN_TOKEN_V2, subject, enhancement, addressIdConverter, verificationStrategies, gasCalculator);
        assertFalse(subject.matches(attempt));
    }
}
