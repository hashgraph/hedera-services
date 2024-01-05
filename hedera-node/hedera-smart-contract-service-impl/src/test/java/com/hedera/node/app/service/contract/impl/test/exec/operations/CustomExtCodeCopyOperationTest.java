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

package com.hedera.node.app.service.contract.impl.test.exec.operations;

import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.assertSameResult;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.contract.impl.exec.AddressChecks;
import com.hedera.node.app.service.contract.impl.exec.operations.CustomExtCodeCopyOperation;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.UnderflowException;
import org.hyperledger.besu.evm.operation.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomExtCodeCopyOperationTest {
    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private AddressChecks addressChecks;

    @Mock
    private MessageFrame frame;

    @Mock
    private EVM evm;

    private CustomExtCodeCopyOperation subject;

    @BeforeEach
    void setup() {
        subject = new CustomExtCodeCopyOperation(gasCalculator, addressChecks);
    }

    @Test
    void catchesUnderflowWhenStackIsEmpty() {
        given(frame.getStackItem(0)).willThrow(UnderflowException.class);
        final var expected = new Operation.OperationResult(0L, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
        assertSameResult(expected, subject.execute(frame, evm));
    }

    @Test
    void rejectsMissingUserAddress() {
        given(frame.getStackItem(1)).willReturn(Bytes32.leftPad(Bytes.ofUnsignedLong(1L)));
        given(frame.getStackItem(2)).willReturn(Bytes32.leftPad(Bytes.ofUnsignedLong(2L)));
        given(frame.getStackItem(3)).willReturn(Bytes32.leftPad(Bytes.ofUnsignedLong(3)));
        givenWellKnownFrameWith(Address.fromHexString("0x123"));
        final var expected = new Operation.OperationResult(123L, INVALID_SOLIDITY_ADDRESS);
        assertSameResult(expected, subject.execute(frame, evm));
    }

    @Test
    void hasSpecialBehaviorForNonUserAccount() {
        given(addressChecks.isNonUserAccount(Address.fromHexString("0x123"))).willReturn(true);
        given(frame.getStackItem(anyInt())).willReturn(Bytes32.leftPad(Bytes.ofUnsignedLong(1)));
        givenWellKnownFrameWith(Address.fromHexString("0x123"));
        final var expected = new Operation.OperationResult(123L, null);
        assertSameResult(expected, subject.execute(frame, evm));
        verify(frame).popStackItems(4);
    }

    @Test
    void hasNormalBehaviorForUserAccount() {
        given(frame.getStackItem(anyInt())).willReturn(Bytes32.leftPad(Bytes.ofUnsignedLong(1)));
        given(addressChecks.isPresent(Address.fromHexString("0x123"), frame)).willReturn(true);
        givenWellKnownFrameWith(Address.fromHexString("0x123"));
        given(frame.popStackItem()).willReturn(Bytes32.leftPad(Bytes.ofUnsignedLong(1)));
        final var expected = new Operation.OperationResult(123L, INSUFFICIENT_GAS);
        assertSameResult(expected, subject.execute(frame, evm));
    }

    private void givenWellKnownFrameWith(final Address to) {
        given(frame.getStackItem(0)).willReturn(to);
        given(gasCalculator.extCodeCopyOperationGasCost(eq(frame), anyLong(), anyLong()))
                .willReturn(123L);
    }
}
