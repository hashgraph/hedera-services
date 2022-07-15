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

import static org.hyperledger.besu.evm.internal.Words.clampedToLong;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiPredicate;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.fluent.SimpleAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaExtCodeCopyOperationTest {

    @Mock private WorldUpdater worldUpdater;

    @Mock private GasCalculator gasCalculator;

    @Mock private MessageFrame mf;

    @Mock private EVM evm;
    @Mock private BiPredicate<Address, MessageFrame> addressValidator;

    private HederaExtCodeCopyOperation subject;

    private final String ETH_ADDRESS = "0xc257274276a4e539741ca11b590b9447b26a8051";
    private final Address ETH_ADDRESS_INSTANCE = Address.fromHexString(ETH_ADDRESS);
    private final Bytes MEM_OFFSET = Bytes.of(5);
    private final Bytes NUM_BYTES = Bytes.of(10);
    private final long OPERATION_COST = 1_000L;
    private final long WARM_READ_COST = 100L;
    private final long ACTUAL_COST = OPERATION_COST + WARM_READ_COST;
    private final Account account = new SimpleAccount(ETH_ADDRESS_INSTANCE, 0, Wei.ONE);

    @BeforeEach
    void setUp() {
        subject = new HederaExtCodeCopyOperation(gasCalculator, addressValidator);
    }

    @Test
    void executeResolvesToInvalidSolidityAddress() {
        // given:
        given(mf.getStackItem(0)).willReturn(ETH_ADDRESS_INSTANCE);
        given(mf.getStackItem(1)).willReturn(MEM_OFFSET);
        given(mf.getStackItem(3)).willReturn(NUM_BYTES);
        given(
                        gasCalculator.extCodeCopyOperationGasCost(
                                mf, clampedToLong(MEM_OFFSET), clampedToLong(NUM_BYTES)))
                .willReturn(OPERATION_COST);
        given(gasCalculator.getWarmStorageReadCost()).willReturn(WARM_READ_COST);
        given(addressValidator.test(any(), any())).willReturn(false);

        // when:
        var opResult = subject.execute(mf, evm);

        // then:
        assertEquals(
                Optional.of(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS),
                opResult.getHaltReason());
        assertEquals(OptionalLong.of(ACTUAL_COST), opResult.getGasCost());
    }

    @Test
    void successfulExecution() {
        // given:
        given(mf.getStackItem(0)).willReturn(ETH_ADDRESS_INSTANCE);
        given(mf.getStackItem(1)).willReturn(MEM_OFFSET);
        given(mf.getStackItem(3)).willReturn(NUM_BYTES);
        given(mf.getWorldUpdater()).willReturn(worldUpdater);
        given(
                        gasCalculator.extCodeCopyOperationGasCost(
                                mf, clampedToLong(MEM_OFFSET), clampedToLong(NUM_BYTES)))
                .willReturn(OPERATION_COST);
        given(gasCalculator.getWarmStorageReadCost()).willReturn(WARM_READ_COST);
        // and:
        given(worldUpdater.get(ETH_ADDRESS_INSTANCE)).willReturn(account);
        // and:
        given(mf.popStackItem())
                .willReturn(ETH_ADDRESS_INSTANCE)
                .willReturn(MEM_OFFSET)
                .willReturn(MEM_OFFSET)
                .willReturn(NUM_BYTES);
        given(mf.warmUpAddress(ETH_ADDRESS_INSTANCE)).willReturn(true);
        given(mf.getRemainingGas()).willReturn(2000L);
        given(mf.getWorldUpdater()).willReturn(worldUpdater);
        // and:
        given(
                        gasCalculator.extCodeCopyOperationGasCost(
                                mf, clampedToLong(MEM_OFFSET), clampedToLong(NUM_BYTES)))
                .willReturn(OPERATION_COST);
        given(gasCalculator.getWarmStorageReadCost()).willReturn(WARM_READ_COST);
        given(addressValidator.test(any(), any())).willReturn(true);

        // when:
        var opResult = subject.execute(mf, evm);

        // then:
        assertEquals(Optional.empty(), opResult.getHaltReason());
        assertEquals(OptionalLong.of(ACTUAL_COST), opResult.getGasCost());
    }
}
