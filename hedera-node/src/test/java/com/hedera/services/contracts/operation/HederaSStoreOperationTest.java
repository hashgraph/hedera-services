/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.contracts.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.gascalculator.StorageGasCalculator;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.contracts.HederaWorldUpdater;
import com.hedera.services.stream.proto.SidecarType;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaSStoreOperationTest {
    @Mock private GasCalculator gasCalculator;
    @Mock private MessageFrame messageFrame;
    @Mock private EVM evm;
    @Mock private HederaWorldUpdater worldUpdater;
    @Mock private MutableAccount mutableAccount;
    @Mock private EvmAccount evmAccount;
    private final Bytes keyBytesMock = Bytes.of(1, 2, 3, 4);
    private final Bytes valueBytesMock = Bytes.of(4, 3, 2, 1);
    @Mock private BlockValues hederaBlockValues;
    @Mock private StorageGasCalculator storageGasCalculator;
    @Mock private GlobalDynamicProperties dynamicProperties;

    private HederaSStoreOperation subject;

    @BeforeEach
    void setUp() {
        subject = new HederaSStoreOperation(gasCalculator, storageGasCalculator, dynamicProperties);
    }

    @Test
    void executesCorrectly() {
        givenValidContext(keyBytesMock, valueBytesMock);
        given(dynamicProperties.enabledSidecars())
                .willReturn(EnumSet.of(SidecarType.CONTRACT_STATE_CHANGE));
        var frameStack = new ArrayDeque<MessageFrame>();
        frameStack.add(messageFrame);
        given(messageFrame.getMessageFrameStack()).willReturn(frameStack);
        given(messageFrame.getMessageFrameStack()).willReturn(frameStack);
        given(mutableAccount.getStorageValue(UInt256.fromBytes(UInt256.fromBytes(keyBytesMock))))
                .willReturn(UInt256.fromBytes(valueBytesMock));
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        final var parentUpdater = mock(HederaWorldState.Updater.class);
        given(worldUpdater.parentUpdater()).willReturn(Optional.of(parentUpdater));
        final var stateChanges = new TreeMap<Address, Map<Bytes, Pair<Bytes, Bytes>>>();
        given(parentUpdater.getStateChanges()).willReturn(stateChanges);
        given(mutableAccount.getAddress()).willReturn(Address.fromHexString("0x123"));

        final var result = subject.execute(messageFrame, evm);

        final var expected = new Operation.OperationResult(OptionalLong.of(10), Optional.empty());

        assertEquals(expected.getGasCost(), result.getGasCost());
        assertEquals(expected.getHaltReason(), result.getHaltReason());
        verify(mutableAccount).setStorageValue(any(), any());
        verify(messageFrame).storageWasUpdated(any(), any());
        final var slotMap = stateChanges.get(mutableAccount.getAddress());
        assertEquals(
                UInt256.fromBytes(valueBytesMock),
                slotMap.get(UInt256.fromBytes(keyBytesMock)).getLeft());
    }

    @Test
    void haltsWithIllegalStateChange() {
        givenValidContext(keyBytesMock, valueBytesMock);

        given(messageFrame.isStatic()).willReturn(true);

        final var result = subject.execute(messageFrame, evm);

        final var expected =
                new Operation.OperationResult(
                        OptionalLong.of(10),
                        Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));

        assertEquals(expected.getGasCost(), result.getGasCost());
        assertEquals(expected.getHaltReason(), result.getHaltReason());

        verify(mutableAccount, never()).setStorageValue(any(), any());
        verify(messageFrame, never()).storageWasUpdated(any(), any());
    }

    @Test
    void haltsWithInsufficientGas() {
        final UInt256 keyBytes = UInt256.fromBytes(keyBytesMock);
        final UInt256 valueBytes = UInt256.fromBytes(valueBytesMock);
        final var recipientAccount = Address.fromHexString("0x0001");

        given(messageFrame.popStackItem()).willReturn(keyBytes).willReturn(valueBytes);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(messageFrame.getRecipientAddress()).willReturn(recipientAccount);
        given(worldUpdater.getAccount(recipientAccount)).willReturn(evmAccount);
        given(evmAccount.getMutable()).willReturn(mutableAccount);
        given(mutableAccount.getStorageValue(any())).willReturn(keyBytes);
        given(gasCalculator.calculateStorageCost(any(), any(), any())).willReturn(10L);
        given(messageFrame.warmUpStorage(any(), any())).willReturn(true);
        given(messageFrame.isStatic()).willReturn(false);
        given(messageFrame.getRemainingGas()).willReturn(0L);

        final var result = subject.execute(messageFrame, evm);

        final var expected =
                new Operation.OperationResult(
                        OptionalLong.of(10), Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));

        assertEquals(expected.getGasCost(), result.getGasCost());
        assertEquals(expected.getHaltReason(), result.getHaltReason());

        verify(mutableAccount, never()).setStorageValue(any(), any());
        verify(messageFrame, never()).storageWasUpdated(any(), any());
    }

    @Test
    void haltsWhenMutableAccountIsUnavailable() {
        final UInt256 keyBytes = UInt256.fromBytes(keyBytesMock);
        final UInt256 valueBytes = UInt256.fromBytes(valueBytesMock);
        final var recipientAccount = Address.fromHexString("0x0001");

        given(messageFrame.popStackItem()).willReturn(keyBytes).willReturn(valueBytes);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(messageFrame.getRecipientAddress()).willReturn(recipientAccount);
        given(worldUpdater.getAccount(recipientAccount)).willReturn(evmAccount);

        final var result = subject.execute(messageFrame, evm);

        final var expected =
                new Operation.OperationResult(
                        OptionalLong.empty(),
                        Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));

        assertEquals(expected.getGasCost(), result.getGasCost());
        assertEquals(expected.getHaltReason(), result.getHaltReason());

        verify(mutableAccount, never()).setStorageValue(any(), any());
        verify(messageFrame, never()).storageWasUpdated(any(), any());
    }

    @Test
    void executesWithZero() {
        final UInt256 key = UInt256.fromBytes(keyBytesMock);
        final UInt256 value = UInt256.fromBytes(Bytes.fromHexString("0x12345678"));

        givenValidContext(key, value);
        given(mutableAccount.getStorageValue(any())).willReturn(UInt256.ZERO);

        final var frameGasCost = 10L;
        given(storageGasCalculator.gasCostOfStorageIn(messageFrame)).willReturn(frameGasCost);

        final var result = subject.execute(messageFrame, evm);

        final var expected =
                new Operation.OperationResult(OptionalLong.of(frameGasCost), Optional.empty());

        assertEquals(expected.getGasCost(), result.getGasCost());
        assertEquals(expected.getHaltReason(), result.getHaltReason());

        verify(mutableAccount).setStorageValue(key, value);
        verify(messageFrame).storageWasUpdated(key, value);
        verify(worldUpdater).addSbhRefund(frameGasCost);
    }

    private void givenValidContext(Bytes key, Bytes value) {
        final UInt256 keyBytes = UInt256.fromBytes(key);
        final UInt256 valueBytes = UInt256.fromBytes(value);
        final var recipientAccount = Address.fromHexString("0x0001");

        given(messageFrame.popStackItem()).willReturn(keyBytes).willReturn(valueBytes);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(messageFrame.getRecipientAddress()).willReturn(recipientAccount);
        given(worldUpdater.getAccount(recipientAccount)).willReturn(evmAccount);
        given(evmAccount.getMutable()).willReturn(mutableAccount);
        given(gasCalculator.calculateStorageCost(any(), any(), any())).willReturn(10L);
        given(messageFrame.warmUpStorage(any(), any())).willReturn(true);
        given(messageFrame.isStatic()).willReturn(false);
        given(messageFrame.getRemainingGas()).willReturn(300L);

        given(mutableAccount.getStorageValue(any())).willReturn(keyBytes);
    }
}
