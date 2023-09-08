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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.EIP_1014_ADDRESS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsSystemContract;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AttemptFactory;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import java.nio.ByteBuffer;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HtsSystemContractTest {
    @Mock
    private HtsCall call;

    @Mock
    private MessageFrame frame;

    @Mock
    private HtsCallAttempt callAttempt;

    @Mock
    private AttemptFactory attemptFactory;

    @Mock
    private GasCalculator gasCalculator;

    private HtsSystemContract subject;

    @BeforeEach
    void setUp() {
        subject = new HtsSystemContract(gasCalculator, attemptFactory);
    }

    @Test
    void returnsResultFromImpliedCall() {
        givenValidCallAttempt();

        final var pricedResult = gasOnly(successResult(ByteBuffer.allocate(1), 123L));
        given(call.execute()).willReturn(pricedResult);

        assertSame(pricedResult.fullResult(), subject.computeFully(Bytes.EMPTY, frame));
    }

    @Test
    void invalidCallAttemptNotImplemented() {
        givenCallAttempt();
        assertThrows(AssertionError.class, () -> subject.computeFully(Bytes.EMPTY, frame));
    }

    @Test
    void callWithNonGasCostNotImplemented() {
        givenValidCallAttempt();
        final var pricedResult = new HtsCall.PricedResult(successResult(ByteBuffer.allocate(1), 123L), 456L);
        given(call.execute()).willReturn(pricedResult);

        assertThrows(AssertionError.class, () -> subject.computeFully(Bytes.EMPTY, frame));
    }

    private void givenValidCallAttempt() {
        givenCallAttempt();
        given(callAttempt.asCallFrom(EIP_1014_ADDRESS)).willReturn(call);
    }

    private void givenCallAttempt() {
        given(frame.getSenderAddress()).willReturn(EIP_1014_ADDRESS);
        given(attemptFactory.createFrom(Bytes.EMPTY, frame)).willReturn(callAttempt);
    }
}
