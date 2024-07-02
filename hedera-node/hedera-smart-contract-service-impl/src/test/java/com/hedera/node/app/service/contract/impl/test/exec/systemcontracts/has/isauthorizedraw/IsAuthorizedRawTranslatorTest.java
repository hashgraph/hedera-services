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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.has.isauthorizedraw;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.APPROVED_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.messageHash;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.signature;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.HasCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.hbarallowance.HbarAllowanceTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.isauthorizedraw.IsAuthorizedRawCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.isauthorizedraw.IsAuthorizedRawTranslator;
import com.hedera.node.app.service.contract.impl.exec.v051.Version051FeatureFlags;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class IsAuthorizedRawTranslatorTest {

    @Mock(strictness = Strictness.LENIENT) // might not use `configuration()`
    private HasCallAttempt attempt;

    @Mock
    private HederaWorldUpdater.Enhancement enhancement;

    @Mock
    private SystemContractGasCalculator gasCalculator;

    private IsAuthorizedRawTranslator subject;

    @BeforeEach
    void setUp() {
        final var featureFlags = new Version051FeatureFlags();
        subject = new IsAuthorizedRawTranslator(featureFlags);
    }

    @Test
    void matchesIsAuthorizedRawWhenEnabled() {
        given(attempt.configuration()).willReturn(getTestConfiguration(true));
        given(attempt.selector()).willReturn(IsAuthorizedRawTranslator.IS_AUTHORIZED_RAW.selector());
        var matches = subject.matches(attempt);
        assertTrue(matches);
    }

    @Test
    void doesNotMatchIsAuthorizedRawWhenDisabled() {
        given(attempt.configuration()).willReturn(getTestConfiguration(false));
        given(attempt.selector()).willReturn(IsAuthorizedRawTranslator.IS_AUTHORIZED_RAW.selector());
        var matches = subject.matches(attempt);
        assertFalse(matches);
    }

    @Test
    void failsOnInvalidSelector() {
        given(attempt.configuration()).willReturn(getTestConfiguration(true));
        given(attempt.selector()).willReturn(HbarAllowanceTranslator.HBAR_ALLOWANCE_PROXY.selector());
        final var matches = subject.matches(attempt);
        assertFalse(matches);
    }

    @Test
    void callFromIsAuthorizedRawTest() {
        given(attempt.configuration()).willReturn(getTestConfiguration(true));
        final Bytes inputBytes = Bytes.wrapByteBuffer(IsAuthorizedRawTranslator.IS_AUTHORIZED_RAW.encodeCall(
                Tuple.of(APPROVED_HEADLONG_ADDRESS, messageHash, signature)));
        givenCommonForCall(inputBytes);

        final var call = subject.callFrom(attempt);
        assertThat(call).isInstanceOf(IsAuthorizedRawCall.class);
    }

    private void givenCommonForCall(Bytes inputBytes) {
        given(attempt.inputBytes()).willReturn(inputBytes.toArray());
        given(attempt.selector()).willReturn(inputBytes.slice(0, 4).toArray());
        given(attempt.enhancement()).willReturn(enhancement);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);
    }

    @NonNull
    Configuration getTestConfiguration(final boolean enableIsAuthorizedRaw) {
        return HederaTestConfigBuilder.create()
                .withValue("contracts.systemContract.accountService.isAuthorizedRawEnabled", enableIsAuthorizedRaw)
                .getOrCreateConfig();
    }
}
