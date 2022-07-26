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

import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiPredicate;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.FixedStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaExtCodeHashOperationTest {

    @Mock private HederaStackedWorldStateUpdater worldUpdater;

    @Mock private Account account;

    @Mock private GasCalculator gasCalculator;

    @Mock private MessageFrame mf;

    @Mock private EVM evm;

    @Mock private BiPredicate<Address, MessageFrame> addressValidator;

    private HederaExtCodeHashOperation subject;

    private final String ETH_ADDRESS = "0xc257274276a4e539741ca11b590b9447b26a8051";
    private final Address ETH_ADDRESS_INSTANCE = Address.fromHexString(ETH_ADDRESS);
    private final long OPERATION_COST = 1_000L;
    private final long WARM_READ_COST = 100L;
    private final long ACTUAL_COST = OPERATION_COST + WARM_READ_COST;

    @BeforeEach
    void setUp() {
        subject = new HederaExtCodeHashOperation(gasCalculator, addressValidator);
        given(gasCalculator.extCodeHashOperationGasCost()).willReturn(OPERATION_COST);
        given(gasCalculator.getWarmStorageReadCost()).willReturn(WARM_READ_COST);
    }

    @Test
    void executeResolvesToInvalidSolidityAddress() {
        given(mf.popStackItem()).willReturn(ETH_ADDRESS_INSTANCE);
        given(addressValidator.test(any(), any())).willReturn(false);

        var opResult = subject.execute(mf, evm);

        assertEquals(
                Optional.of(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS),
                opResult.getHaltReason());
        assertEquals(OptionalLong.of(ACTUAL_COST), opResult.getGasCost());
    }

    @Test
    void executeResolvesToInsufficientGas() {
        givenMessageFrameWithRemainingGas(ACTUAL_COST - 1L);
        given(addressValidator.test(any(), any())).willReturn(true);

        var opResult = subject.execute(mf, evm);

        assertEquals(Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS), opResult.getHaltReason());
        assertEquals(OptionalLong.of(ACTUAL_COST), opResult.getGasCost());
    }

    @Test
    void executeHappyPathWithEmptyAccount() {
        givenMessageFrameWithRemainingGas(ACTUAL_COST + 1L);
        given(account.isEmpty()).willReturn(true);
        given(addressValidator.test(any(), any())).willReturn(true);

        var opResult = subject.execute(mf, evm);

        assertEquals(OptionalLong.of(ACTUAL_COST), opResult.getGasCost());
    }

    @Test
    void executeHappyPathWithAccount() {
        givenMessageFrameWithRemainingGas(ACTUAL_COST + 1L);
        given(account.getCodeHash()).willReturn(Hash.hash(Bytes.of(1)));
        given(addressValidator.test(any(), any())).willReturn(true);

        var opResult = subject.execute(mf, evm);

        assertEquals(OptionalLong.of(ACTUAL_COST), opResult.getGasCost());
    }

    @Test
    void executeWithGasRemainingAsActualCost() {
        givenMessageFrameWithRemainingGas(ACTUAL_COST);
        given(account.isEmpty()).willReturn(false);
        given(account.getCodeHash()).willReturn(Hash.hash(Bytes.of(1)));
        given(addressValidator.test(any(), any())).willReturn(true);

        var opResult = subject.execute(mf, evm);

        assertEquals(OptionalLong.of(ACTUAL_COST), opResult.getGasCost());
    }

    @Test
    void executeThrowsInsufficientStackItems() {
        given(mf.popStackItem()).willThrow(FixedStack.UnderflowException.class);

        var opResult = subject.execute(mf, evm);

        assertEquals(Optional.of(INSUFFICIENT_STACK_ITEMS), opResult.getHaltReason());
        assertEquals(OptionalLong.of(ACTUAL_COST), opResult.getGasCost());
    }

    private void givenMessageFrameWithRemainingGas(long gas) {
        given(mf.popStackItem()).willReturn(ETH_ADDRESS_INSTANCE);
        given(mf.getWorldUpdater()).willReturn(worldUpdater);
        given(mf.warmUpAddress(ETH_ADDRESS_INSTANCE)).willReturn(true);
        given(mf.getRemainingGas()).willReturn(gas);
        given(worldUpdater.get(ETH_ADDRESS_INSTANCE)).willReturn(account);
    }
}
