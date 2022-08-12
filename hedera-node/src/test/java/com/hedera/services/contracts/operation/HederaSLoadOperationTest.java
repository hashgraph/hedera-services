/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.TOO_MANY_STACK_ITEMS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.HederaWorldState;
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
class HederaSLoadOperationTest {
    final Address recipientAccount = Address.fromHexString("0x0001");

    HederaSLoadOperation subject;

    @Mock GasCalculator gasCalculator;

    @Mock MessageFrame messageFrame;

    @Mock EVM evm;

    @Mock HederaStackedWorldStateUpdater worldUpdater;

    @Mock EvmAccount evmAccount;

    final Bytes keyBytesMock = Bytes.of(1, 2, 3, 4);
    final Bytes valueBytesMock = Bytes.of(4, 3, 2, 1);

    @Mock private GlobalDynamicProperties dynamicProperties;

    @BeforeEach
    void setUp() {
        givenValidContext();
        subject = new HederaSLoadOperation(gasCalculator, dynamicProperties);
    }

    @Test
    void executesProperlyWithColdSuccess() {
        givenAdditionalContext(keyBytesMock, valueBytesMock);
        given(messageFrame.warmUpStorage(any(), any())).willReturn(true);
        given(messageFrame.getRemainingGas()).willReturn(300L);
        given(messageFrame.warmUpStorage(any(), any())).willReturn(false);
        given(dynamicProperties.enabledSidecars()).willReturn(EnumSet.noneOf(SidecarType.class));

        final var coldResult = subject.execute(messageFrame, evm);

        final var expectedColdResult =
                new Operation.OperationResult(OptionalLong.of(20L), Optional.empty());

        assertEquals(expectedColdResult.getGasCost(), coldResult.getGasCost());
        assertEquals(expectedColdResult.getHaltReason(), coldResult.getHaltReason());
        assertEquals(expectedColdResult.getPcIncrement(), coldResult.getPcIncrement());
    }

    @Test
    void executesProperlyWithWarmSuccess() {
        givenAdditionalContext(keyBytesMock, valueBytesMock);
        given(messageFrame.warmUpStorage(any(), any())).willReturn(true);
        given(messageFrame.getRemainingGas()).willReturn(300L);
        given(dynamicProperties.enabledSidecars())
                .willReturn(EnumSet.of(SidecarType.CONTRACT_STATE_CHANGE));
        final var frameStack = new ArrayDeque<MessageFrame>();
        frameStack.add(messageFrame);
        given(messageFrame.getMessageFrameStack()).willReturn(frameStack);
        given(evmAccount.getStorageValue(UInt256.fromBytes(UInt256.fromBytes(keyBytesMock))))
                .willReturn(UInt256.fromBytes(valueBytesMock));
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        final var parentUpdater = mock(HederaWorldState.Updater.class);
        given(worldUpdater.parentUpdater()).willReturn(Optional.of(parentUpdater));
        final var stateChanges = new TreeMap<Address, Map<Bytes, Pair<Bytes, Bytes>>>();
        given(parentUpdater.getStateChanges()).willReturn(stateChanges);

        final var warmResult = subject.execute(messageFrame, evm);

        final var expectedWarmResult =
                new Operation.OperationResult(OptionalLong.of(30L), Optional.empty());
        assertEquals(expectedWarmResult.getGasCost(), warmResult.getGasCost());
        assertEquals(expectedWarmResult.getHaltReason(), warmResult.getHaltReason());
        assertEquals(expectedWarmResult.getPcIncrement(), warmResult.getPcIncrement());
        final var slotMap = stateChanges.get(evmAccount.getAddress());
        assertEquals(
                UInt256.fromBytes(valueBytesMock),
                slotMap.get(UInt256.fromBytes(keyBytesMock)).getLeft());
    }

    @Test
    void executeHaltsForInsufficientGas() {
        givenAdditionalContext(keyBytesMock, valueBytesMock);
        given(messageFrame.warmUpStorage(any(), any())).willReturn(true);
        given(messageFrame.getRemainingGas()).willReturn(300L);
        given(messageFrame.getRemainingGas()).willReturn(0L);

        final var expectedHaltResult =
                new Operation.OperationResult(
                        OptionalLong.of(30L), Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));

        final var haltResult = subject.execute(messageFrame, evm);

        assertEquals(expectedHaltResult.getGasCost(), haltResult.getGasCost());
        assertEquals(expectedHaltResult.getHaltReason(), haltResult.getHaltReason());
        assertEquals(expectedHaltResult.getPcIncrement(), haltResult.getPcIncrement());
    }

    @Test
    void executeWithUnderFlowException() {
        givenAdditionalContext(keyBytesMock, valueBytesMock);
        given(messageFrame.popStackItem()).willThrow(new FixedStack.UnderflowException());
        final var result = subject.execute(messageFrame, evm);
        assertEquals(INSUFFICIENT_STACK_ITEMS, result.getHaltReason().get());
    }

    @Test
    void executeWithOverFlowException() {
        givenAdditionalContext(keyBytesMock, valueBytesMock);
        given(messageFrame.warmUpStorage(any(), any())).willReturn(true);
        given(messageFrame.getRemainingGas()).willReturn(300L);
        given(dynamicProperties.enabledSidecars())
                .willReturn(EnumSet.of(SidecarType.CONTRACT_STATE_CHANGE));
        var frameStack = new ArrayDeque<MessageFrame>();
        frameStack.add(messageFrame);

        given(messageFrame.getMessageFrameStack()).willReturn(frameStack);
        doThrow(new FixedStack.OverflowException()).when(messageFrame).pushStackItem(any());

        final var result = subject.execute(messageFrame, evm);
        assertTrue(result.getHaltReason().isPresent());
        assertEquals(TOO_MANY_STACK_ITEMS, result.getHaltReason().get());
    }

    private void givenAdditionalContext(Bytes key, Bytes value) {
        final UInt256 keyBytes = UInt256.fromBytes(key);
        final UInt256 valueBytes = UInt256.fromBytes(value);

        given(messageFrame.popStackItem()).willReturn(keyBytes).willReturn(valueBytes);
        given(worldUpdater.get(recipientAccount)).willReturn(evmAccount);
        given(evmAccount.getAddress()).willReturn(Address.fromHexString("0x123"));
    }

    private void givenValidContext() {
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(messageFrame.getRecipientAddress()).willReturn(recipientAccount);

        given(gasCalculator.getSloadOperationGasCost()).willReturn(10L);
        given(gasCalculator.getWarmStorageReadCost()).willReturn(20L);
        given(gasCalculator.getColdSloadCost()).willReturn(10L);
    }
}
