/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec.processors;

import static com.hedera.node.app.service.contract.impl.exec.processors.ProcessorModule.INITIAL_CONTRACT_NONCE;
import static com.hedera.node.app.service.contract.impl.exec.processors.ProcessorModule.REQUIRE_CODE_DEPOSIT_TO_SUCCEED;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason;
import com.hedera.node.app.service.contract.impl.exec.processors.CustomContractCreationProcessor;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.app.spi.workflows.ResourceExhaustedException;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.contractvalidation.ContractValidationRule;
import org.hyperledger.besu.evm.contractvalidation.MaxCodeSizeRule;
import org.hyperledger.besu.evm.contractvalidation.PrefixCodeRule;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomContractCreationProcessorTest {
    private static final List<ContractValidationRule> STANDARD_RULES =
            List.of(MaxCodeSizeRule.of(0x6000), PrefixCodeRule.of());

    @Mock
    private EVM evm;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private MutableAccount contract;

    @Mock
    private MessageFrame frame;

    @Mock
    private OperationTracer tracer;

    @Mock
    private ProxyWorldUpdater worldUpdater;

    private CustomContractCreationProcessor subject;

    @BeforeEach
    void setUp() {
        subject = new CustomContractCreationProcessor(
                evm, gasCalculator, REQUIRE_CODE_DEPOSIT_TO_SUCCEED, STANDARD_RULES, INITIAL_CONTRACT_NONCE);
    }

    @Test
    void createsExpectedContractIfDidNotAlreadyExist() {
        given(frame.getSenderAddress()).willReturn(NON_SYSTEM_LONG_ZERO_ADDRESS);
        given(frame.getContractAddress()).willReturn(EIP_1014_ADDRESS);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(contract.getCode()).willReturn(Bytes.EMPTY);
        given(worldUpdater.getOrCreate(EIP_1014_ADDRESS)).willReturn(contract);
        given(frame.getValue()).willReturn(WEI_VALUE);

        subject.start(frame, tracer);

        verify(worldUpdater).tryTransfer(NON_SYSTEM_LONG_ZERO_ADDRESS, EIP_1014_ADDRESS, WEI_VALUE.toLong(), false);
        verify(contract).setNonce(INITIAL_CONTRACT_NONCE);
        verify(frame).setState(MessageFrame.State.CODE_EXECUTING);
    }

    @Test
    void haltsOnFailedCreation() {
        given(frame.getContractAddress()).willReturn(EIP_1014_ADDRESS);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.getOrCreate(EIP_1014_ADDRESS)).willThrow(ResourceExhaustedException.class);
        final Optional<ExceptionalHaltReason> expectedHaltReason =
                Optional.of(CustomExceptionalHaltReason.CONTRACT_ENTITY_LIMIT_REACHED);

        subject.start(frame, tracer);

        verify(frame).setExceptionalHaltReason(expectedHaltReason);
        verify(frame).setState(MessageFrame.State.EXCEPTIONAL_HALT);
        verify(tracer).traceAccountCreationResult(frame, expectedHaltReason);
    }

    @Test
    void haltsOnFailedTransfer() {
        given(frame.getSenderAddress()).willReturn(NON_SYSTEM_LONG_ZERO_ADDRESS);
        given(frame.getContractAddress()).willReturn(EIP_1014_ADDRESS);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(contract.getCode()).willReturn(Bytes.EMPTY);
        given(worldUpdater.getOrCreate(EIP_1014_ADDRESS)).willReturn(contract);
        given(frame.getValue()).willReturn(WEI_VALUE);
        final var maybeReasonToHalt = Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE);
        given(worldUpdater.tryTransfer(NON_SYSTEM_LONG_ZERO_ADDRESS, EIP_1014_ADDRESS, WEI_VALUE.toLong(), false))
                .willReturn(maybeReasonToHalt);

        subject.start(frame, tracer);

        verify(frame).setExceptionalHaltReason(maybeReasonToHalt);
        verify(frame).setState(MessageFrame.State.EXCEPTIONAL_HALT);
        verify(tracer, never()).traceAccountCreationResult(frame, maybeReasonToHalt);
    }

    @Test
    void haltsWithInsufficientGasIfContractExistsWithNonce() {
        given(frame.getContractAddress()).willReturn(EIP_1014_ADDRESS);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(contract.getNonce()).willReturn(1L);
        given(worldUpdater.getOrCreate(EIP_1014_ADDRESS)).willReturn(contract);

        subject.start(frame, tracer);

        verify(worldUpdater, never())
                .tryTransfer(NON_SYSTEM_LONG_ZERO_ADDRESS, EIP_1014_ADDRESS, WEI_VALUE.toLong(), false);
        verify(contract, never()).setNonce(INITIAL_CONTRACT_NONCE);
        final var maybeReasonToHalt = Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS);
        verify(frame).setExceptionalHaltReason(maybeReasonToHalt);
        verify(frame).setState(MessageFrame.State.EXCEPTIONAL_HALT);
        verify(tracer).traceAccountCreationResult(frame, maybeReasonToHalt);
    }

    @Test
    void haltsWithInsufficientGasIfContractExistsWithNonEmptyCode() {
        given(frame.getContractAddress()).willReturn(EIP_1014_ADDRESS);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(contract.getCode()).willReturn(CONTRACT_CODE.getBytes());
        given(worldUpdater.getOrCreate(EIP_1014_ADDRESS)).willReturn(contract);

        subject.start(frame, tracer);

        verify(worldUpdater, never())
                .tryTransfer(NON_SYSTEM_LONG_ZERO_ADDRESS, EIP_1014_ADDRESS, WEI_VALUE.toLong(), false);
        verify(contract, never()).setNonce(INITIAL_CONTRACT_NONCE);
        final var maybeReasonToHalt = Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS);
        verify(frame).setExceptionalHaltReason(maybeReasonToHalt);
        verify(frame).setState(MessageFrame.State.EXCEPTIONAL_HALT);
        verify(tracer).traceAccountCreationResult(frame, maybeReasonToHalt);
    }
}
