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

package com.hedera.node.app.service.contract.impl.test.exec.operations;

import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.MISSING_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.exec.utils.TestHelpers.assertSameResult;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;

import com.hedera.node.app.service.contract.impl.exec.AddressChecks;
import com.hedera.node.app.service.contract.impl.exec.operations.CustomExtCodeHashOperation;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.FixedStack;
import org.hyperledger.besu.evm.operation.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomExtCodeHashOperationTest {
    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private AddressChecks addressChecks;

    @Mock
    private MessageFrame frame;

    @Mock
    private EVM evm;

    private CustomExtCodeHashOperation subject;

    @BeforeEach
    void setUp() {
        subject = new CustomExtCodeHashOperation(gasCalculator, addressChecks);
    }

    @Test
    void catchesUnderflowWhenStackIsEmpty() {
        given(frame.getStackItem(0)).willThrow(FixedStack.UnderflowException.class);
        final var expected = new Operation.OperationResult(0L, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
        assertSameResult(expected, subject.execute(frame, evm));
    }

    @Test
    void rejectsMissingNonSystemAddress() {
        doCallRealMethod().when(addressChecks).isMissing(any(), any());
        givenWellKnownFrameWith(Address.fromHexString("0x123"));
        final var expected = new Operation.OperationResult(123L, MISSING_ADDRESS);
        assertSameResult(expected, subject.execute(frame, evm));
    }

    @Test
    void delegatesForPresentAddress() {
        givenWellKnownFrameWith(Address.fromHexString("0x123"));
        given(frame.popStackItem()).willReturn(Bytes32.leftPad(Bytes.ofUnsignedLong(1)));
        final var expected = new Operation.OperationResult(123L, INSUFFICIENT_GAS);
        assertSameResult(expected, subject.execute(frame, evm));
    }

    private void givenWellKnownFrameWith(final Address to) {
        given(frame.getStackItem(0)).willReturn(to);
        given(gasCalculator.extCodeHashOperationGasCost()).willReturn(123L);
    }
}
