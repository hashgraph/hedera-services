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

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.assertSameResult;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.contract.impl.exec.FeatureFlags;
import com.hedera.node.app.service.contract.impl.exec.operations.CustomCreate2Operation;
import java.lang.reflect.Field;
import java.util.Deque;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.collections.undo.UndoSet;
import org.hyperledger.besu.collections.undo.UndoTable;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.frame.TxValues;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

class CustomCreate2OperationTest extends CreateOperationTestBase {
    private static final MutableBytes MUTABLE_INITCODE = MutableBytes.wrap(new byte[] {0x01, 0x02, 0x03});
    private static final Address EIP_1014_ADDRESS = Address.fromHexString("5a86fe448f4811ccf76b71a442aa2e5849168ee8");

    @Mock
    private FeatureFlags featureFlags;

    @Mock
    private WorldUpdater updater;

    @Mock
    private TxValues txValues;

    @Mock
    private UndoTable<Address, Bytes32, Bytes32> undoTable;

    @Mock
    private Deque<MessageFrame> messageFrameStack;

    @Mock
    private UndoSet<Address> warmedUpAddresses;

    private CustomCreate2Operation subject;

    @BeforeEach
    void setUp() {
        subject = new CustomCreate2Operation(gasCalculator, featureFlags);
    }

    @Test
    void returnsInvalidWhenDisabled() {
        final var expected = new Operation.OperationResult(0L, ExceptionalHaltReason.INVALID_OPERATION);
        assertSameResult(expected, subject.execute(frame, evm));
    }

    @Test
    void failsWhenPendingContractIsHollowAccountAndLazyCreationDisabled() {
        givenSpawnPrereqs(4);
        given(gasCalculator.create2OperationGasCost(frame)).willReturn(GAS_COST);
        given(frame.getStackItem(0)).willReturn(Bytes.ofUnsignedLong(VALUE));
        given(frame.readMemory(anyLong(), anyLong())).willReturn(INITCODE);
        given(frame.readMutableMemory(anyLong(), anyLong())).willReturn(MUTABLE_INITCODE);
        given(featureFlags.isCreate2Enabled(frame)).willReturn(true);
        given(worldUpdater.isHollowAccount(EIP_1014_ADDRESS)).willReturn(true);

        final var expected = new Operation.OperationResult(GAS_COST, null);
        assertSameResult(expected, subject.execute(frame, evm));

        verify(worldUpdater, never()).setupInternalAliasedCreate(RECIEVER_ADDRESS, EIP_1014_ADDRESS);
        verify(frame).popStackItems(4);
        verify(frame).pushStackItem(UInt256.ZERO);
        verify(featureFlags).isImplicitCreationEnabled(frame);
    }

    @Test
    void finalizesHollowAccountWhenPendingContractIsHollowAccountAndLazyCreationEnabled()
            throws NoSuchFieldException, IllegalAccessException {
        final var frameCaptor = ArgumentCaptor.forClass(MessageFrame.class);
        givenSpawnPrereqs(4);
        given(gasCalculator.create2OperationGasCost(frame)).willReturn(GAS_COST);
        given(frame.getStackItem(0)).willReturn(Bytes.ofUnsignedLong(VALUE));
        given(frame.readMemory(anyLong(), anyLong())).willReturn(INITCODE);
        given(frame.readMutableMemory(anyLong(), anyLong())).willReturn(MUTABLE_INITCODE);
        given(featureFlags.isCreate2Enabled(frame)).willReturn(true);
        given(worldUpdater.isHollowAccount(EIP_1014_ADDRESS)).willReturn(true);
        given(featureFlags.isImplicitCreationEnabled(frame)).willReturn(true);

        given(txValues.transientStorage()).willReturn(undoTable);
        given(txValues.messageFrameStack()).willReturn(messageFrameStack);
        given(txValues.warmedUpAddresses()).willReturn(warmedUpAddresses);
        given(txValues.maxStackSize()).willReturn(1024);
        given(undoTable.mark()).willReturn(1L);

        final Field worldUdaterField = MessageFrame.class.getDeclaredField("worldUpdater");
        worldUdaterField.setAccessible(true);
        worldUdaterField.set(frame, updater);

        final Field txValuesField = MessageFrame.class.getDeclaredField("txValues");
        txValuesField.setAccessible(true);
        txValuesField.set(frame, txValues);

        final var expected = new Operation.OperationResult(GAS_COST, null);
        assertSameResult(expected, subject.execute(frame, evm));

        verify(worldUpdater).setupInternalAliasedCreate(RECIEVER_ADDRESS, EIP_1014_ADDRESS);

        verify(messageFrameStack).addFirst(frameCaptor.capture());
        final var childFrame = frameCaptor.getValue();
        childFrame.setState(MessageFrame.State.COMPLETED_SUCCESS);
        childFrame.notifyCompletion();
        verify(frame).pushStackItem(Words.fromAddress(EIP_1014_ADDRESS));
    }
}
