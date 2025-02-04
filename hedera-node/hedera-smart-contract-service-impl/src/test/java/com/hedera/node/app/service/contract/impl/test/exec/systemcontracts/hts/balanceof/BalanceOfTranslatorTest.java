/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.balanceof;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsSystemContract.HTS_167_CONTRACT_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsSystemContract.HTS_16C_CONTRACT_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.balanceof.BalanceOfTranslator.BALANCE_OF;
import static com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.CallAttemptHelpers.prepareHtsAttemptWithSelectorWithContractID;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.metrics.ContractMetrics;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.balanceof.BalanceOfTranslator;
import com.hedera.node.app.service.contract.impl.exec.utils.SystemContractMethodRegistry;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BalanceOfTranslatorTest {

    @Mock
    private ContractMetrics contractMetrics;

    @Mock
    private SystemContractGasCalculator gasCalculator;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private HederaWorldUpdater.Enhancement enhancement;

    @Mock
    private VerificationStrategies verificationStrategies;

    private final SystemContractMethodRegistry systemContractMethodRegistry = new SystemContractMethodRegistry();

    private BalanceOfTranslator subject;
    private HtsCallAttempt attempt;

    @BeforeEach
    void setUp() {
        subject = new BalanceOfTranslator(systemContractMethodRegistry, contractMetrics);
    }

    @Test
    void validateMatchingContractIDTest() {
        attempt = prepareHtsAttemptWithSelectorWithContractID(
                HTS_167_CONTRACT_ID,
                BALANCE_OF,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                systemContractMethodRegistry);

        assertThat(attempt.isMethod(BALANCE_OF)).isPresent();
    }

    @Test
    void validateNonMatchingContractIDTest() {
        attempt = prepareHtsAttemptWithSelectorWithContractID(
                HTS_16C_CONTRACT_ID,
                BALANCE_OF,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                systemContractMethodRegistry);

        assertThat(attempt.isMethod(BALANCE_OF)).isNotPresent();
    }
}
