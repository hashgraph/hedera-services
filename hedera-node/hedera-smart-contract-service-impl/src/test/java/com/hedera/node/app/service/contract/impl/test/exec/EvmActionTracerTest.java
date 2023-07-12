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

package com.hedera.node.app.service.contract.impl.test.exec;

import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.CONFIG_CONTEXT_VARIABLE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.hapi.streams.ContractActionType;
import com.hedera.node.app.service.contract.impl.exec.EvmActionTracer;
import com.hedera.node.app.service.contract.impl.exec.utils.ActionStack;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import java.util.Optional;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvmActionTracerTest {
    @Mock
    private ActionStack actionStack;

    @Mock
    private MessageFrame frame;

    private EvmActionTracer subject;

    @BeforeEach
    void setUp() {
        subject = new EvmActionTracer(actionStack);
    }

    @Test
    void customInitIsNoopWithoutActionSidecars() {
        givenNoActionSidecars();

        subject.customInit(frame);

        verifyNoInteractions(actionStack);
    }

    @Test
    void customInitTracksTopLevel() {
        givenSidecarsOnly();

        subject.customInit(frame);

        verify(actionStack).pushActionOfTopLevel(frame);
    }

    @Test
    void customFinalizeNoopIfNoActionSidecars() {
        givenNoActionSidecars();

        subject.customFinalize(frame);

        verifyNoInteractions(actionStack);
    }

    @Test
    void customFinalizeNoopIfNotValidatingActions() {
        givenSidecarsOnly();

        subject.customFinalize(frame);

        verifyNoInteractions(actionStack);
    }

    @Test
    void customFinalizeSanitizesActionsIfValidating() {
        givenActionSidecarsAndValidation();

        subject.customFinalize(frame);

        verify(actionStack).sanitizeFinalActionsAndLogAnomalies(eq(frame), any(Logger.class), eq(Level.WARN));
    }

    @Test
    void postExecNoopWithoutActionSidecars() {
        givenNoActionSidecars();

        subject.tracePostExecution(frame, new Operation.OperationResult(123, null));

        verifyNoInteractions(actionStack);
    }

    @Test
    void postExecNoopIfCodeExecutingState() {
        givenSidecarsOnly();
        given(frame.getState()).willReturn(MessageFrame.State.CODE_EXECUTING);

        subject.tracePostExecution(frame, new Operation.OperationResult(123, null));

        verifyNoInteractions(actionStack);
    }

    @Test
    void postExecTracksIntermediateIfSuspended() {
        givenSidecarsOnly();
        given(frame.getState()).willReturn(MessageFrame.State.CODE_SUSPENDED);

        subject.tracePostExecution(frame, new Operation.OperationResult(123, null));

        verify(actionStack).pushActionOfIntermediate(frame);
    }

    @Test
    void postExecFinalizesIfNotSuspended() {
        givenActionSidecarsAndValidation();
        given(frame.getState()).willReturn(MessageFrame.State.COMPLETED_SUCCESS);

        subject.tracePostExecution(frame, new Operation.OperationResult(123, null));

        verify(actionStack).finalizeLastActionIn(frame, true);
    }

    @Test
    void precompileTraceIsNoopIfNoSidecars() {
        givenNoActionSidecars();

        subject.customTracePrecompileResult(frame, ContractActionType.SYSTEM);

        verifyNoInteractions(actionStack);
    }

    @Test
    void precompileTraceIsNoopIfHaltedHederaPrecompile() {
        givenActionSidecarsAndValidation();
        given(frame.getState()).willReturn(MessageFrame.State.EXCEPTIONAL_HALT);

        subject.customTracePrecompileResult(frame, ContractActionType.PRECOMPILE);

        verifyNoInteractions(actionStack);
    }

    @Test
    void precompileTraceIsTrackedIfNotHalted() {
        givenSidecarsOnly();
        given(frame.getState()).willReturn(MessageFrame.State.COMPLETED_FAILED);

        subject.customTracePrecompileResult(frame, ContractActionType.PRECOMPILE);

        verify(actionStack).finalizeLastActionAsPrecompileIn(frame, ContractActionType.PRECOMPILE, false);
    }

    @Test
    void accountCreationTraceIsNoopIfNoSidecars() {
        givenNoActionSidecars();

        subject.traceAccountCreationResult(frame, Optional.empty());

        verifyNoInteractions(actionStack);
    }

    private void givenNoActionSidecars() {
        givenConfig(false, false);
    }

    private void givenActionSidecarsAndValidation() {
        givenConfig(true, true);
    }

    private void givenSidecarsOnly() {
        givenConfig(true, false);
    }

    private void givenConfig(final boolean actionSidecars, final boolean validation) {
        final var config = HederaTestConfigBuilder.create()
                .withValue("contracts.sidecars", actionSidecars ? "CONTRACT_ACTION" : "CONTRACT_STATE_CHANGE")
                .withValue("contracts.sidecarValidationEnabled", validation ? "true" : "false")
                .getOrCreateConfig();
        given(frame.getContextVariable(CONFIG_CONTEXT_VARIABLE)).willReturn(config);
    }
}
