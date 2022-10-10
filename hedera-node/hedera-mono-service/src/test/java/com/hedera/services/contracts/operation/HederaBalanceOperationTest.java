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

import static com.hedera.services.contracts.operation.HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.TOO_MANY_STACK_ITEMS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;

import java.util.function.BiPredicate;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.FixedStack;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaBalanceOperationTest {

    @Mock private GasCalculator gasCalculator;
    @Mock private MessageFrame frame;
    @Mock private EVM evm;
    @Mock private WorldUpdater worldUpdater;
    @Mock private Account account;
    @Mock private BiPredicate<Address, MessageFrame> addressValidator;

    private final long gas = 100L;

    private HederaBalanceOperation subject;

    @BeforeEach
    void setUp() {
        subject = new HederaBalanceOperation(gasCalculator, addressValidator);
        givenAddress();
        given(gasCalculator.getWarmStorageReadCost()).willReturn(1600L);
        given(gasCalculator.getBalanceOperationGasCost()).willReturn(100L);
    }

    @Test
    void haltsWithInsufficientStackItemsOperationResultWhenGetsStackItem() {
        given(frame.getStackItem(anyInt())).willThrow(new FixedStack.UnderflowException());
        thenOperationWillFailWithReason(INSUFFICIENT_STACK_ITEMS);
    }

    @Test
    void haltsWithInsufficientStackItemsWhenPopsStackItem() {
        given(frame.popStackItem()).willThrow(new FixedStack.UnderflowException());
        given(addressValidator.test(any(), any())).willReturn(true);

        thenOperationWillFailWithReason(INSUFFICIENT_STACK_ITEMS);
    }

    @Test
    void haltsWithTooManyStackItemsWhenPopsStackItem() {
        given(frame.popStackItem()).willThrow(new FixedStack.OverflowException());
        given(addressValidator.test(any(), any())).willReturn(true);

        thenOperationWillFailWithReason(TOO_MANY_STACK_ITEMS);
    }

    @Test
    void haltsWithInvalidSolidityAddressOperationResult() {
        given(addressValidator.test(any(), any())).willReturn(false);

        thenOperationWillFailWithReason(INVALID_SOLIDITY_ADDRESS);
    }

    @Test
    void haltsWithInsufficientGasOperationResult() {
        given(frame.popStackItem()).willReturn(Bytes.EMPTY);
        given(frame.warmUpAddress(any())).willReturn(true);
        given(frame.getRemainingGas()).willReturn(0L);
        given(addressValidator.test(any(), any())).willReturn(true);

        thenOperationWillFailWithReason(INSUFFICIENT_GAS);
    }

    @Test
    void returnsOperationResultWithoutException() {
        given(worldUpdater.get(any())).willReturn(account);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.popStackItem()).willReturn(Bytes.EMPTY);
        given(frame.warmUpAddress(any())).willReturn(true);
        given(frame.getRemainingGas()).willReturn(10_000L);
        given(addressValidator.test(any(), any())).willReturn(true);

        final var result = subject.execute(frame, evm);

        assertTrue(result.getHaltReason().isEmpty());
    }

    private void givenAddress() {
        given(frame.getStackItem(anyInt())).willReturn(Bytes.EMPTY);
        try (MockedStatic<Words> theMock = mockStatic(Words.class)) {
            theMock.when(() -> Words.toAddress(Bytes.EMPTY)).thenReturn(Address.ZERO);
        }
    }

    private void thenOperationWillFailWithReason(ExceptionalHaltReason reason) {
        final var result = subject.execute(frame, evm);
        assertEquals(reason, result.getHaltReason().get());
    }
}
