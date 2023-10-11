/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.update;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_SYSTEM_ACCOUNT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.update.UpdateDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.update.UpdateTranslator;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateTranslatorTest {
    @Mock
    private HtsCallAttempt attempt;

    @Mock
    private HederaWorldUpdater.Enhancement enhancement;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private VerificationStrategy verificationStrategy;

    private UpdateTranslator subject;

    private final UpdateDecoder decoder = new UpdateDecoder();

    private final Tuple hederaToken = Tuple.of(
            "name",
            "symbol",
            NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
            "memo",
            true,
            1000L,
            false,
            // TokenKey
            new Tuple[] {},
            // Expiry
            Tuple.of(0L, NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, 0L));

    @BeforeEach
    void setUp() {
        subject = new UpdateTranslator(decoder);
    }

    @Test
    void matchesUpdateV1Test() {
        given(attempt.selector()).willReturn(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

    @Test
    void matchesUpdateV2Test() {
        given(attempt.selector()).willReturn(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V2.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

    @Test
    void matchesUpdateV3Test() {
        given(attempt.selector()).willReturn(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V3.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

    @Test
    void callFromUpdateTest() {
        Tuple tuple = new Tuple(NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, hederaToken);
        Bytes inputBytes = Bytes.wrapByteBuffer(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION.encodeCall(tuple));
        given(attempt.input()).willReturn(inputBytes);
        given(attempt.selector()).willReturn(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION.selector());
        given(attempt.enhancement()).willReturn(enhancement);
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(addressIdConverter.convertSender(any())).willReturn(NON_SYSTEM_ACCOUNT_ID);
        given(addressIdConverter.convert(any())).willReturn(NON_SYSTEM_ACCOUNT_ID);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);

        final var call = subject.callFrom(attempt);
        assertThat(call).isInstanceOf(DispatchForResponseCodeHtsCall.class);
    }
}
