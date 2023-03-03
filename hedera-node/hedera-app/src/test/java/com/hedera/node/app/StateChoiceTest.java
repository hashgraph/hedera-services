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

package com.hedera.node.app;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.mono.ServicesState;
import com.hedera.node.app.service.mono.sigs.EventExpansion;
import com.hedera.node.app.service.mono.state.DualStateAccessor;
import com.hedera.node.app.service.mono.state.org.StateMetadata;
import com.hedera.node.app.service.mono.txns.ProcessLogic;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.node.app.state.merkle.MerkleHederaState;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.events.Event;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StateChoiceTest {
    @Mock
    private Event event;

    @Mock
    private Round round;

    @Mock
    private SwirldDualState dualState;

    @Mock
    private Consumer<MerkleHederaState> migration;

    @Mock
    private EventExpansion eventExpansion;

    @Mock
    private ProcessLogic processLogic;

    @Mock
    private DualStateAccessor dualStateAccessor;

    @Mock
    private WorkingStateAccessor workingStateAccessor;

    @Mock
    private HederaApp app;

    @Mock
    private StateMetadata metadata;

    private final ServicesMain subject = new ServicesMain();

    @Test
    void usesServicesStateIfNoWorkflowEnabled() {
        assertInstanceOf(ServicesState.class, subject.stateWithWorkflowsEnabled(false));
    }

    @Test
    void usesMerkleHederaStateIfWorkflowsEnabled() {
        final var workflowsState = subject.stateWithWorkflowsEnabled(true);
        assertInstanceOf(MerkleHederaState.class, workflowsState);
    }

    @Test
    void passesMigrationFactoryToHederaState() {
        final var workflowsState = subject.newMerkleHederaState(ignore -> migration);
        workflowsState.migrate(-1);
        Mockito.verify(migration).accept(workflowsState);
    }

    @Test
    void constructsExpectedPreHandle() {
        final var workflowsState = subject.newMerkleHederaState(ignore -> migration);
        workflowsState.setMetadata(metadata);

        given(metadata.app()).willReturn(app);
        given(app.eventExpansion()).willReturn(eventExpansion);

        workflowsState.preHandle(event);

        verify(eventExpansion).expandAllSigs(event, workflowsState);
    }

    @Test
    void constructsExpectedHandle() {
        final var workflowsState = subject.newMerkleHederaState(ignore -> migration);
        workflowsState.setMetadata(metadata);

        given(metadata.app()).willReturn(app);
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);
        given(app.workingStateAccessor()).willReturn(workingStateAccessor);
        given(app.logic()).willReturn(processLogic);

        workflowsState.handleConsensusRound(round, dualState);

        verify(dualStateAccessor).setDualState(dualState);
        verify(workingStateAccessor).setHederaState(workflowsState);
        verify(processLogic).incorporateConsensus(round);
    }
}
