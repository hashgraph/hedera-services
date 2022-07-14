package com.hedera.services.contracts.execution;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.CODE_SUCCESS;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.COMPLETED_SUCCESS;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.EXCEPTIONAL_HALT;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.REVERT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.hedera.services.store.contracts.precompile.HTSPrecompiledContract;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaMessageCallProcessorTest {

    private static final String HEDERA_PRECOMPILE_ADDRESS_STRING = "0x1337";
    private static final String HTS_PRECOMPILE_ADDRESS_STRING = "0x167";
    private static final Address HEDERA_PRECOMPILE_ADDRESS =
            Address.fromHexString(HEDERA_PRECOMPILE_ADDRESS_STRING);
    private static final Address HTS_PRECOMPILE_ADDRESS =
            Address.fromHexString(HTS_PRECOMPILE_ADDRESS_STRING);
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

    @Mock private EVM evm;
    @Mock private PrecompileContractRegistry precompiles;
    @Mock private MessageFrame frame;
    @Mock private OperationTracer operationTrace;
    @Mock private WorldUpdater worldUpdater;
    @Mock private PrecompiledContract nonHtsPrecompile;
    @Mock private HTSPrecompiledContract htsPrecompile;

    HederaMessageCallProcessor subject;

    @BeforeEach
    void setup() {
        subject =
                new HederaMessageCallProcessor(
                        evm,
                        precompiles,
                        Map.of(
                                HEDERA_PRECOMPILE_ADDRESS_STRING, nonHtsPrecompile,
                                HTS_PRECOMPILE_ADDRESS_STRING, htsPrecompile));
    }

    @Test
    void callsHederaPrecompile() {
        given(frame.getRemainingGas()).willReturn(1337L);
        given(frame.getInputData()).willReturn(Bytes.of(1));
        given(frame.getContractAddress()).willReturn(HEDERA_PRECOMPILE_ADDRESS);
        given(nonHtsPrecompile.gasRequirement(any())).willReturn(GAS_ONE);
        given(nonHtsPrecompile.computePrecompile(any(), eq(frame))).willReturn(RESULT);

        subject.start(frame, operationTrace);

        verify(nonHtsPrecompile).computePrecompile(Bytes.of(1), frame);
        verify(operationTrace).tracePrecompileCall(frame, GAS_ONE, Bytes.of(1));
        verify(frame).decrementRemainingGas(GAS_ONE);
        verify(frame).setOutputData(Bytes.of(1));
        verify(frame).setState(COMPLETED_SUCCESS);
        verifyNoMoreInteractions(nonHtsPrecompile, frame, operationTrace);
    }

    @Test
    void treatsHtsPrecompileSpecial() {
        given(frame.getRemainingGas()).willReturn(1337L);
        given(frame.getInputData()).willReturn(Bytes.of(1));
        given(frame.getContractAddress()).willReturn(HTS_PRECOMPILE_ADDRESS);
        given(frame.getState()).willReturn(CODE_SUCCESS);
        given(htsPrecompile.computeCosted(any(), eq(frame)))
                .willReturn(Pair.of(GAS_ONE, Bytes.of(1)));

        subject.start(frame, operationTrace);

        verify(frame).getState();
        verify(operationTrace).tracePrecompileCall(frame, GAS_ONE, Bytes.of(1));
        verify(frame).decrementRemainingGas(GAS_ONE);
        verify(frame).setOutputData(Bytes.of(1));
        verify(frame).setState(COMPLETED_SUCCESS);
        verifyNoMoreInteractions(htsPrecompile, frame, operationTrace);
    }

    @Test
    void callsParent() {
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(frame.getRecipientAddress()).willReturn(RECIPIENT_ADDRESS);
        given(frame.getSenderAddress()).willReturn(SENDER_ADDRESS);
        given(frame.getContractAddress()).willReturn(Address.fromHexString("0x1"));

        subject.start(frame, operationTrace);

        verify(frame).setState(MessageFrame.State.CODE_EXECUTING);
        verifyNoMoreInteractions(nonHtsPrecompile, frame, operationTrace);
    }

    @Test
    void insufficientGasReverts() {
        given(frame.getRemainingGas()).willReturn(GAS_ONE_K);
        given(frame.getInputData()).willReturn(Bytes.EMPTY);
        given(nonHtsPrecompile.gasRequirement(any())).willReturn(GAS_ONE_M);
        given(nonHtsPrecompile.computePrecompile(any(), any())).willReturn(NO_RESULT);

        subject.executeHederaPrecompile(nonHtsPrecompile, frame, operationTrace);

        verify(frame).setExceptionalHaltReason(Optional.of(INSUFFICIENT_GAS));
        verify(frame).setState(EXCEPTIONAL_HALT);
        verify(frame).decrementRemainingGas(GAS_ONE_K);
        verify(nonHtsPrecompile).computePrecompile(Bytes.EMPTY, frame);
        verify(operationTrace).tracePrecompileCall(frame, GAS_ONE_M, null);
        verifyNoMoreInteractions(nonHtsPrecompile, frame, operationTrace);
    }

    @Test
    void precompileError() {
        given(frame.getRemainingGas()).willReturn(GAS_ONE_K);
        given(frame.getInputData()).willReturn(Bytes.EMPTY);
        given(nonHtsPrecompile.gasRequirement(any())).willReturn(GAS_ONE);
        given(nonHtsPrecompile.computePrecompile(any(), any())).willReturn(NO_RESULT);

        subject.executeHederaPrecompile(nonHtsPrecompile, frame, operationTrace);

        verify(frame).setState(EXCEPTIONAL_HALT);
        verify(operationTrace).tracePrecompileCall(frame, GAS_ONE, null);
        verifyNoMoreInteractions(nonHtsPrecompile, frame, operationTrace);
    }

    @Test
    void revertedPrecompileReturns() {
        given(frame.getInputData()).willReturn(Bytes.EMPTY);
        given(htsPrecompile.computeCosted(any(), any())).willReturn(null);
        given(frame.getState()).willReturn(REVERT);

        subject.executeHederaPrecompile(htsPrecompile, frame, operationTrace);

        verify(frame).getState();
        verify(htsPrecompile).computeCosted(Bytes.EMPTY, frame);
        verifyNoMoreInteractions(htsPrecompile, frame, operationTrace);
    }
}
