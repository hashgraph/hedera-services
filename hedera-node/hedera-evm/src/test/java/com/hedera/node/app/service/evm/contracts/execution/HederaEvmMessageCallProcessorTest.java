/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.evm.contracts.execution;

import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.CODE_EXECUTING;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.COMPLETED_SUCCESS;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.EXCEPTIONAL_HALT;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.REVERT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.hedera.node.app.service.evm.contracts.execution.traceability.DefaultHederaTracer;
import com.hedera.node.app.service.evm.store.contracts.AbstractLedgerEvmWorldUpdater;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.fluent.SimpleAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.frame.MessageFrame.State;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaEvmMessageCallProcessorTest {

    private static final String HEDERA_PRECOMPILE_ADDRESS_STRING = "0x1337";
    private static final Address HEDERA_PRECOMPILE_ADDRESS =
            Address.fromHexString(HEDERA_PRECOMPILE_ADDRESS_STRING);
    private static final Address RECIPIENT_ADDRESS = Address.fromHexString("0xcafecafe01");
    private static final Address SENDER_ADDRESS = Address.fromHexString("0xcafecafe02");
    private static final PrecompiledContract.PrecompileContractResult NO_RESULT =
            new PrecompiledContract.PrecompileContractResult(
                    null, true, MessageFrame.State.COMPLETED_FAILED, Optional.empty());
    private static final PrecompiledContract.PrecompileContractResult RESULT =
            new PrecompiledContract.PrecompileContractResult(
                    Bytes.of(1), true, MessageFrame.State.COMPLETED_SUCCESS, Optional.empty());
    private static final long GAS_ONE = 1L;
    private static final long GAS_ONE_K = 1_000L;
    private static final long GAS_ONE_M = 1_000_000L;
    HederaEvmMessageCallProcessor subject;
    @Mock private EVM evm;
    @Mock private PrecompileContractRegistry precompiles;
    @Mock private MessageFrame frame;
    @Mock private DefaultHederaTracer hederaEvmOperationTracer;
    @Mock private WorldUpdater worldUpdater;
    @Mock private PrecompiledContract nonHtsPrecompile;
    @Mock private AbstractLedgerEvmWorldUpdater updater;

    @BeforeEach
    void setup() {
        subject =
                new HederaEvmMessageCallProcessor(
                        evm,
                        precompiles,
                        Map.of(HEDERA_PRECOMPILE_ADDRESS_STRING, nonHtsPrecompile));
    }

    @Test
    void callsHederaPrecompile() {
        given(frame.getRemainingGas()).willReturn(1337L);
        given(frame.getInputData()).willReturn(Bytes.of(1));
        given(frame.getContractAddress()).willReturn(HEDERA_PRECOMPILE_ADDRESS);
        given(nonHtsPrecompile.gasRequirement(any())).willReturn(GAS_ONE);
        given(nonHtsPrecompile.computePrecompile(any(), eq(frame))).willReturn(RESULT);

        subject.start(frame, hederaEvmOperationTracer);

        verify(nonHtsPrecompile).computePrecompile(Bytes.of(1), frame);
        verify(hederaEvmOperationTracer).tracePrecompileCall(frame, GAS_ONE, Bytes.of(1));
        verify(frame).decrementRemainingGas(GAS_ONE);
        verify(frame).setOutputData(Bytes.of(1));
        verify(frame).setState(COMPLETED_SUCCESS);
        verify(frame).getState();
    }

    @Test
    void callsParent() {
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(frame.getRecipientAddress()).willReturn(RECIPIENT_ADDRESS);
        given(frame.getSenderAddress()).willReturn(SENDER_ADDRESS);
        given(frame.getContractAddress()).willReturn(Address.fromHexString("0x1"));
        doCallRealMethod().when(frame).setState(CODE_EXECUTING);

        subject.start(frame, hederaEvmOperationTracer);

        verifyNoMoreInteractions(nonHtsPrecompile, frame);
    }

    @Test
    void callsParentWithNonTokenAccountReceivingNoValue() {
        final var sender = new SimpleAccount(SENDER_ADDRESS, 0L, Wei.ZERO);
        sender.setBalance(Wei.of(123));
        final var receiver = new SimpleAccount(RECIPIENT_ADDRESS, 0L, Wei.ZERO);

        given(frame.getWorldUpdater()).willReturn(updater);
        given(frame.getValue()).willReturn(Wei.of(123));
        given(frame.getRecipientAddress()).willReturn(RECIPIENT_ADDRESS);
        given(frame.getSenderAddress()).willReturn(SENDER_ADDRESS);
        given(frame.getContractAddress()).willReturn(Address.fromHexString("0x1"));
        given(updater.getSenderAccount(frame)).willReturn(sender);
        given(updater.getOrCreate(RECIPIENT_ADDRESS)).willReturn(receiver);
        given(updater.get(RECIPIENT_ADDRESS)).willReturn(receiver);
        doCallRealMethod().when(frame).setState(CODE_EXECUTING);

        subject.start(frame, hederaEvmOperationTracer);

        verifyNoMoreInteractions(nonHtsPrecompile, frame);
    }

    @Test
    void callsPrecompileWithLoadedEmptyOutputAndGasRequirementWorks() {
        given(frame.getRemainingGas()).willReturn(GAS_ONE_K);
        final var precompile = Address.fromHexString("0x0000000000000000000000000000000000001337");
        given(nonHtsPrecompile.gasRequirement(any())).willReturn(GAS_ONE);
        given(nonHtsPrecompile.computePrecompile(any(), eq(frame))).willReturn(RESULT);
        given(frame.getContractAddress()).willReturn(precompile);

        subject.start(frame, hederaEvmOperationTracer);

        verify(hederaEvmOperationTracer)
                .tracePrecompileCall(frame, 1L, Bytes.fromHexString("0x01"));
        verify(frame).setState(State.COMPLETED_SUCCESS);
    }

    @Test
    void callingHtsPrecompileHalts() {
        given(frame.getRemainingGas()).willReturn(GAS_ONE_K);
        final var precompile = Address.fromHexString("0x0000000000000000000000000000000000001337");
        given(nonHtsPrecompile.getName()).willReturn("HTS");
        given(frame.getContractAddress()).willReturn(precompile);

        subject.start(frame, hederaEvmOperationTracer);

        verify(hederaEvmOperationTracer).tracePrecompileCall(frame, 0L, null);
        verify(frame).setState(State.EXCEPTIONAL_HALT);
    }

    @Test
    void rejectsValueBeingSentToTokenAccount() {
        given(frame.getWorldUpdater()).willReturn(updater);
        given(frame.getValue()).willReturn(Wei.of(123));
        given(frame.getRecipientAddress()).willReturn(RECIPIENT_ADDRESS);
        given(frame.getContractAddress()).willReturn(Address.fromHexString("0x1"));
        given(updater.isTokenAddress(RECIPIENT_ADDRESS)).willReturn(true);
        doCallRealMethod().when(frame).setState(EXCEPTIONAL_HALT);

        subject.start(frame, hederaEvmOperationTracer);

        verify(frame)
                .setExceptionalHaltReason(Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));
        verify(frame).setState(MessageFrame.State.EXCEPTIONAL_HALT);
        verifyNoMoreInteractions(nonHtsPrecompile, frame);
    }

    @Test
    void insufficientGasReverts() {
        given(frame.getRemainingGas()).willReturn(GAS_ONE_K);
        given(frame.getInputData()).willReturn(Bytes.EMPTY);
        given(frame.getState()).willReturn(CODE_EXECUTING);
        given(nonHtsPrecompile.gasRequirement(any())).willReturn(GAS_ONE_M);
        given(nonHtsPrecompile.computePrecompile(any(), any())).willReturn(NO_RESULT);

        subject.executeHederaPrecompile(nonHtsPrecompile, frame, hederaEvmOperationTracer);

        verify(frame).setExceptionalHaltReason(Optional.of(INSUFFICIENT_GAS));
        verify(frame).setState(EXCEPTIONAL_HALT);
        verify(frame).decrementRemainingGas(GAS_ONE_K);
        verify(nonHtsPrecompile).computePrecompile(Bytes.EMPTY, frame);
        verify(nonHtsPrecompile).getName();
        verify(hederaEvmOperationTracer).tracePrecompileCall(frame, GAS_ONE_M, null);
        verifyNoMoreInteractions(nonHtsPrecompile, frame, hederaEvmOperationTracer);
    }

    @Test
    void precompileError() {
        given(frame.getRemainingGas()).willReturn(GAS_ONE_K);
        given(frame.getInputData()).willReturn(Bytes.EMPTY);
        given(frame.getState()).willReturn(CODE_EXECUTING);
        given(nonHtsPrecompile.gasRequirement(any())).willReturn(GAS_ONE);
        given(nonHtsPrecompile.computePrecompile(any(), any())).willReturn(NO_RESULT);

        subject.executeHederaPrecompile(nonHtsPrecompile, frame, hederaEvmOperationTracer);

        verify(frame).setState(EXCEPTIONAL_HALT);
        verify(nonHtsPrecompile).getName();
        verify(hederaEvmOperationTracer).tracePrecompileCall(frame, GAS_ONE, null);
        verifyNoMoreInteractions(nonHtsPrecompile, frame, hederaEvmOperationTracer);
    }

    @Test
    void precompileRevert() {
        given(frame.getInputData()).willReturn(Bytes.EMPTY);
        given(frame.getState()).willReturn(REVERT);
        given(nonHtsPrecompile.gasRequirement(any())).willReturn(GAS_ONE);
        given(nonHtsPrecompile.computePrecompile(any(), any())).willReturn(NO_RESULT);

        subject.executeHederaPrecompile(nonHtsPrecompile, frame, hederaEvmOperationTracer);

        verify(hederaEvmOperationTracer).tracePrecompileCall(frame, GAS_ONE, null);
        verify(nonHtsPrecompile).getName();
        verifyNoMoreInteractions(nonHtsPrecompile, frame, hederaEvmOperationTracer);
    }

    @Test
    void executesLazyCreate() {
        given(frame.getSenderAddress()).willReturn(RECIPIENT_ADDRESS);
        given(frame.getRecipientAddress()).willReturn(RECIPIENT_ADDRESS);
        given(frame.getValue()).willReturn(Wei.of(1000L));
        given(frame.getWorldUpdater()).willReturn(updater);
        given(updater.isTokenAddress(RECIPIENT_ADDRESS)).willReturn(false);
        given(updater.get(RECIPIENT_ADDRESS)).willReturn(null);

        subject.start(frame, hederaEvmOperationTracer);

        verify(frame).getState();
    }

    @Test
    void executesLazyCreateFailed() {
        given(frame.getRecipientAddress()).willReturn(RECIPIENT_ADDRESS);
        given(frame.getValue()).willReturn(Wei.of(1000L));
        given(frame.getWorldUpdater()).willReturn(updater);
        given(updater.isTokenAddress(RECIPIENT_ADDRESS)).willReturn(false);
        given(updater.get(RECIPIENT_ADDRESS))
                .willAnswer(
                        invocation -> {
                            given(frame.getState()).willReturn(EXCEPTIONAL_HALT);
                            return null;
                        });

        subject.start(frame, hederaEvmOperationTracer);

        verify(frame).getState();
        verify(frame, never()).getSenderAddress();
    }
}
