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

import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiPredicate;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.fluent.SimpleAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.FixedStack;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaExtCodeSizeOperationTest {
    private final String ethAddress = "0xc257274276a4e539741ca11b590b9447b26a8051";
    private final Address ethAddressInstance = Address.fromHexString(ethAddress);
    private final Account account = new SimpleAccount(ethAddressInstance, 0, Wei.ONE);

    @Mock WorldUpdater worldUpdater;

    @Mock GasCalculator gasCalculator;

    @Mock MessageFrame mf;

    @Mock EVM evm;
    @Mock private BiPredicate<Address, MessageFrame> addressValidator;

    HederaExtCodeSizeOperation subject;

    @BeforeEach
    void setUp() {
        given(gasCalculator.getExtCodeSizeOperationGasCost()).willReturn(10L);
        given(gasCalculator.getWarmStorageReadCost()).willReturn(2L);

        subject = new HederaExtCodeSizeOperation(gasCalculator, addressValidator);
    }

    @Test
    void executeWorldUpdaterResolvesToNull() {
        given(mf.getStackItem(0)).willReturn(ethAddressInstance);
        given(addressValidator.test(any(), any())).willReturn(false);

        var opResult = subject.execute(mf, evm);

        assertEquals(
                Optional.of(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS),
                opResult.getHaltReason());
        assertEquals(OptionalLong.of(12L), opResult.getGasCost());
    }

    @Test
    void executeThrows() {
        given(mf.getStackItem(0)).willThrow(FixedStack.UnderflowException.class);

        var opResult = subject.execute(mf, evm);

        assertEquals(Optional.of(INSUFFICIENT_STACK_ITEMS), opResult.getHaltReason());
        assertEquals(OptionalLong.of(12L), opResult.getGasCost());
    }

    @Test
    void successfulExecution() {
        // given:
        given(mf.getStackItem(0)).willReturn(ethAddressInstance);
        given(mf.getWorldUpdater()).willReturn(worldUpdater);
        // and:
        given(worldUpdater.get(ethAddressInstance)).willReturn(account);
        // and:
        given(mf.popStackItem()).willReturn(ethAddressInstance);
        given(mf.warmUpAddress(ethAddressInstance)).willReturn(true);
        given(mf.getRemainingGas()).willReturn(100L);
        given(addressValidator.test(any(), any())).willReturn(true);

        // when:
        var opResult = subject.execute(mf, evm);

        // then:
        assertEquals(Optional.empty(), opResult.getHaltReason());
        assertEquals(OptionalLong.of(12L), opResult.getGasCost());
    }
}
