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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.iskyc;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.burn.BurnTranslator.BURN_TOKEN_V2;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.iskyc.IsKycTranslator.IS_KYC;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OWNER_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.CallAttemptHelpers.prepareHtsAttemptWithSelector;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.fromHeadlongAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.iskyc.IsKycCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.iskyc.IsKycTranslator;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater.Enhancement;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IsKycTranslatorTest {
    @Mock
    private HtsCallAttempt attempt;

    @Mock
    private SystemContractGasCalculator gasCalculator;

    @Mock
    private Token token;

    @Mock
    private Enhancement enhancement;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private VerificationStrategies verificationStrategies;

    private IsKycTranslator subject;

    @BeforeEach
    void setUp() {
        subject = new IsKycTranslator();
    }

    @Test
    void matchesIsKycTest() {
        attempt = prepareHtsAttemptWithSelector(
                IS_KYC, subject, enhancement, addressIdConverter, verificationStrategies, gasCalculator);
        assertTrue(subject.matches(attempt));
    }

    @Test
    void matchesFailsIfIncorrectSelectorTest() {
        attempt = prepareHtsAttemptWithSelector(
                BURN_TOKEN_V2, subject, enhancement, addressIdConverter, verificationStrategies, gasCalculator);
        assertFalse(subject.matches(attempt));
    }

    @Test
    void callFromTest() {
        final Tuple tuple = new Tuple(FUNGIBLE_TOKEN_HEADLONG_ADDRESS, OWNER_HEADLONG_ADDRESS);
        final Bytes inputBytes = Bytes.wrapByteBuffer(IS_KYC.encodeCall(tuple));
        given(attempt.input()).willReturn(inputBytes);
        given(attempt.linkedToken(fromHeadlongAddress(FUNGIBLE_TOKEN_HEADLONG_ADDRESS)))
                .willReturn(token);
        given(attempt.enhancement()).willReturn(enhancement);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);

        final var call = subject.callFrom(attempt);
        assertThat(call).isInstanceOf(IsKycCall.class);
    }
}
