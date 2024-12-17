/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec.tracers;

import static com.hedera.hapi.streams.ContractActionType.CALL;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.streams.ContractActions;
import com.hedera.node.app.service.contract.impl.exec.tracers.AddOnEvmActionTracer;
import com.hedera.node.app.service.contract.impl.exec.tracers.EvmActionTracer;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AddOnEvmActionTracerTest {
    @Mock
    private EvmActionTracer evmActionTracer;

    @Mock
    private OperationTracer addOnTracer;

    @Mock
    private MessageFrame messageFrame;

    @Mock
    private Operation.OperationResult result;

    @Mock
    private WorldView worldView;

    @Mock
    private Transaction transaction;

    private AddOnEvmActionTracer subject;

    @BeforeEach
    void setUp() {
        subject = new AddOnEvmActionTracer(evmActionTracer, List.of(addOnTracer));
    }

    @Test
    void delegatesTraceOriginAction() {
        subject.traceOriginAction(messageFrame);
        verify(evmActionTracer).traceOriginAction(messageFrame);
    }

    @Test
    void delegatesSanitizeTracedActions() {
        subject.sanitizeTracedActions(messageFrame);
        verify(evmActionTracer).sanitizeTracedActions(messageFrame);
    }

    @Test
    void delegatesTracePrecompileResult() {
        subject.tracePrecompileResult(messageFrame, CALL);
        verify(evmActionTracer).tracePrecompileResult(messageFrame, CALL);
    }

    @Test
    void delegatesContractActions() {
        given(evmActionTracer.contractActions()).willReturn(ContractActions.DEFAULT);
        assertSame(ContractActions.DEFAULT, subject.contractActions());
    }

    @Test
    void delegatesTracePostExecution() {
        subject.tracePostExecution(messageFrame, result);
        verify(evmActionTracer).tracePostExecution(messageFrame, result);
        verify(addOnTracer).tracePostExecution(messageFrame, result);
    }

    @Test
    void delegatesTraceAccountCreationResult() {
        final var reason = Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS);
        subject.traceAccountCreationResult(messageFrame, reason);
        verify(evmActionTracer).traceAccountCreationResult(messageFrame, reason);
        verify(addOnTracer).traceAccountCreationResult(messageFrame, reason);
    }

    @Test
    void delegatesTracePreExecution() {
        subject.tracePreExecution(messageFrame);
        verify(addOnTracer).tracePreExecution(messageFrame);
    }

    @Test
    void delegatesTracePrecompileCall() {
        subject.tracePrecompileCall(messageFrame, 1L, Bytes.EMPTY);
        verify(addOnTracer).tracePrecompileCall(messageFrame, 1L, Bytes.EMPTY);
    }

    @Test
    void delegatesTracePrepareTransaction() {
        subject.tracePrepareTransaction(worldView, transaction);
        verify(addOnTracer).tracePrepareTransaction(worldView, transaction);
    }

    @Test
    void delegatesTraceStartTransaction() {
        subject.traceStartTransaction(worldView, transaction);
        verify(addOnTracer).traceStartTransaction(worldView, transaction);
    }

    @Test
    void delegatesTraceEndTransaction() {
        subject.traceEndTransaction(worldView, transaction, true, Bytes.EMPTY, emptyList(), 1L, 2L);
        verify(addOnTracer).traceEndTransaction(worldView, transaction, true, Bytes.EMPTY, emptyList(), 1L, 2L);
    }

    @Test
    void delegatesTraceContextEnter() {
        subject.traceContextEnter(messageFrame);
        verify(addOnTracer).traceContextEnter(messageFrame);
    }

    @Test
    void delegatesTraceContextReEnter() {
        subject.traceContextReEnter(messageFrame);
        verify(addOnTracer).traceContextReEnter(messageFrame);
    }

    @Test
    void delegatesTraceContextExit() {
        subject.traceContextExit(messageFrame);
        verify(addOnTracer).traceContextExit(messageFrame);
    }

    @Test
    void delegatesExtendedTracing() {
        given(addOnTracer.isExtendedTracing()).willReturn(true);
        assertThat(subject.isExtendedTracing()).isTrue();
    }
}
