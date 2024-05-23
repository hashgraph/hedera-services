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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.has.hbarApprove;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.APPROVED_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.A_NEW_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.B_NEW_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.HasCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.hbarallowance.HbarAllowanceTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.hbarapprove.HbarApproveCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.hbarapprove.HbarApproveTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class HbarApproveTranslatorTest {
    @Mock
    private HasCallAttempt attempt;

    @Mock
    private SystemContractGasCalculator gasCalculator;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private HederaWorldUpdater.Enhancement enhancement;

    private HbarApproveTranslator subject;

    @BeforeEach
    void setUp() {
        subject = new HbarApproveTranslator();
    }

    @Test
    void matchesHbarApprove() {
        given(attempt.selector()).willReturn(HbarApproveTranslator.HBAR_APPROVE.selector());
        var matches = subject.matches(attempt);
        assertTrue(matches);

        given(attempt.selector()).willReturn(HbarApproveTranslator.HBAR_APPROVE_PROXY.selector());
        matches = subject.matches(attempt);
        assertTrue(matches);
    }

    @Test
    void failsOnInvalidSelector() {
        given(attempt.selector()).willReturn(HbarAllowanceTranslator.HBAR_ALLOWANCE_PROXY.selector());
        final var matches = subject.matches(attempt);
        assertFalse(matches);
    }

    @Test
    void callFromHbarApproveProxyTest() {
        final Bytes inputBytes = Bytes.wrapByteBuffer(HbarApproveTranslator.HBAR_APPROVE_PROXY.encodeCall(
                Tuple.of(APPROVED_HEADLONG_ADDRESS, BigInteger.ONE)));
        givenCommonForCall(inputBytes);
        given(attempt.senderId()).willReturn(A_NEW_ACCOUNT_ID);

        final var call = subject.callFrom(attempt);
        assertThat(call).isInstanceOf(HbarApproveCall.class);
    }

    @Test
    void callFromHbarApproveTest() {
        final Bytes inputBytes = Bytes.wrapByteBuffer(HbarApproveTranslator.HBAR_APPROVE.encodeCall(
                Tuple.of(APPROVED_HEADLONG_ADDRESS, UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS, BigInteger.ONE)));
        givenCommonForCall(inputBytes);
        given(addressIdConverter.convert(APPROVED_HEADLONG_ADDRESS)).willReturn(B_NEW_ACCOUNT_ID);
        given(addressIdConverter.convert(UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS)).willReturn(A_NEW_ACCOUNT_ID);

        final var call = subject.callFrom(attempt);
        assertThat(call).isInstanceOf(HbarApproveCall.class);
    }

    private void givenCommonForCall(Bytes inputBytes) {
        given(attempt.inputBytes()).willReturn(inputBytes.toArray());
        given(attempt.selector()).willReturn(inputBytes.slice(0, 4).toArray());
        given(attempt.enhancement()).willReturn(enhancement);
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(addressIdConverter.convert(APPROVED_HEADLONG_ADDRESS)).willReturn(B_NEW_ACCOUNT_ID);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);
    }
}
