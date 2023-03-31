/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.contracts.operation;

import static com.hedera.node.app.service.mono.contracts.operation.CommonCallSetup.commonSetup;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason;
import com.hedera.node.app.service.mono.contracts.sources.EvmSigsVerifier;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import java.util.Map;
import java.util.function.BiPredicate;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaCallOperationTest {
    @Mock private GasCalculator calc;
    @Mock private MessageFrame evmMsgFrame;
    @Mock private EVM evm;
    @Mock private HederaStackedWorldStateUpdater worldUpdater;
    @Mock private Account acc;
    @Mock private EvmSigsVerifier sigsVerifier;
    @Mock private BiPredicate<Address, MessageFrame> addressValidator;
    @Mock private Map<String, PrecompiledContract> precompiledContractMap;

    private final long cost = 100L;
    private HederaCallOperation subject;

    @BeforeEach
    void setup() {
        subject =
                new HederaCallOperation(
                        sigsVerifier, calc, addressValidator, precompiledContractMap);
    }

    @Test
    void haltWithInvalidAddr() {
        commonSetup(evmMsgFrame, worldUpdater, acc);
        given(worldUpdater.get(any())).willReturn(null);
        given(
                        calc.callOperationGasCost(
                                any(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), any(),
                                any(), any()))
                .willReturn(cost);
        given(evmMsgFrame.getStackItem(0)).willReturn(Bytes.EMPTY);
        given(evmMsgFrame.getStackItem(1)).willReturn(Bytes.EMPTY);
        given(evmMsgFrame.getStackItem(2)).willReturn(Bytes.EMPTY);
        given(evmMsgFrame.getStackItem(3)).willReturn(Bytes.EMPTY);
        given(evmMsgFrame.getStackItem(4)).willReturn(Bytes.EMPTY);
        given(evmMsgFrame.getStackItem(5)).willReturn(Bytes.EMPTY);
        given(evmMsgFrame.getStackItem(6)).willReturn(Bytes.EMPTY);
        given(addressValidator.test(any(), any())).willReturn(false);

        var opRes = subject.execute(evmMsgFrame, evm);

        assertEquals(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS, opRes.getHaltReason());
        assertEquals(cost, opRes.getGasCost());
    }

    @Test
    void executesAsExpected() {
        commonSetup(evmMsgFrame, worldUpdater, acc);
        given(
                        calc.callOperationGasCost(
                                any(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), any(),
                                any(), any()))
                .willReturn(cost);
        // and:
        given(evmMsgFrame.getStackItem(0)).willReturn(Bytes.EMPTY);
        given(evmMsgFrame.getStackItem(1)).willReturn(Bytes.EMPTY);
        given(evmMsgFrame.getStackItem(2)).willReturn(Bytes.EMPTY);
        given(evmMsgFrame.getStackItem(3)).willReturn(Bytes.EMPTY);
        given(evmMsgFrame.getStackItem(4)).willReturn(Bytes.EMPTY);
        given(evmMsgFrame.getStackItem(5)).willReturn(Bytes.EMPTY);
        given(evmMsgFrame.getStackItem(6)).willReturn(Bytes.EMPTY);
        // and:
        given(evmMsgFrame.stackSize()).willReturn(20);
        given(evmMsgFrame.getRemainingGas()).willReturn(cost);
        given(evmMsgFrame.getMessageStackDepth()).willReturn(1025);

        given(evmMsgFrame.getContractAddress()).willReturn(Address.ALTBN128_ADD);
        given(evmMsgFrame.getRecipientAddress()).willReturn(Address.ALTBN128_ADD);

        given(worldUpdater.get(any())).willReturn(acc);
        given(acc.getBalance()).willReturn(Wei.of(100));
        given(calc.gasAvailableForChildCall(any(), anyLong(), anyBoolean())).willReturn(10L);
        given(acc.getAddress()).willReturn(Address.ZERO);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                Mockito.anyBoolean(), any(), any(), any(), eq(ContractCall)))
                .willReturn(true);
        given(addressValidator.test(any(), any())).willReturn(true);

        var opRes = subject.execute(evmMsgFrame, evm);
        assertNull(opRes.getHaltReason());
        assertEquals(opRes.getGasCost(), cost);

        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                Mockito.anyBoolean(), any(), any(), any(), eq(ContractCall)))
                .willReturn(false);
        var invalidSignaturesRes = subject.execute(evmMsgFrame, evm);
        assertEquals(
                HederaExceptionalHaltReason.INVALID_SIGNATURE,
                invalidSignaturesRes.getHaltReason());
    }
}
