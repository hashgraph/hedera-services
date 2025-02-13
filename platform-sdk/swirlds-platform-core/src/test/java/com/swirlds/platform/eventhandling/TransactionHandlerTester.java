/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.eventhandling;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.platform.roster.RosterRetriever;
import com.swirlds.platform.state.PlatformMerkleStateRoot;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.state.StateLifecycles;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.service.PlatformStateFacade;
import com.swirlds.platform.state.service.PlatformStateValueAccumulator;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.status.StatusActionSubmitter;
import com.swirlds.platform.system.status.actions.PlatformStatusAction;
import com.swirlds.platform.test.fixtures.state.TestPlatformStateFacade;
import com.swirlds.state.State;
import java.util.ArrayList;
import java.util.List;

/**
 * A helper class for testing the {@link DefaultTransactionHandler}.
 */
public class TransactionHandlerTester {
    private final PlatformStateModifier platformState;
    private final SwirldStateManager swirldStateManager;
    private final DefaultTransactionHandler defaultTransactionHandler;
    private final List<PlatformStatusAction> submittedActions = new ArrayList<>();
    private final List<Round> handledRounds = new ArrayList<>();
    private final StateLifecycles<PlatformMerkleStateRoot> stateLifecycles;
    private final TestPlatformStateFacade platformStateFacade;
    private final PlatformMerkleStateRoot consensusState;

    /**
     * Constructs a new {@link TransactionHandlerTester} with the given {@link AddressBook}.
     *
     * @param addressBook the {@link AddressBook} to use
     */
    public TransactionHandlerTester(final AddressBook addressBook) {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        platformState = new PlatformStateValueAccumulator();

        consensusState = mock(PlatformMerkleStateRoot.class);
        platformStateFacade = mock(TestPlatformStateFacade.class);

        stateLifecycles = mock(StateLifecycles.class);
        when(consensusState.copy()).thenReturn(consensusState);
        when(consensusState.cast()).thenReturn(consensusState);
        when(platformStateFacade.getWritablePlatformStateOf(consensusState)).thenReturn(platformState);

        when(stateLifecycles.onSealConsensusRound(any(), any())).thenReturn(true);
        doAnswer(i -> {
                    handledRounds.add(i.getArgument(0));
                    return null;
                })
                .when(stateLifecycles)
                .onHandleConsensusRound(any(), same(consensusState), any());
        final StatusActionSubmitter statusActionSubmitter = submittedActions::add;
        swirldStateManager = new SwirldStateManager(
                platformContext,
                RosterRetriever.buildRoster(addressBook),
                NodeId.FIRST_NODE_ID,
                statusActionSubmitter,
                new BasicSoftwareVersion(1),
                stateLifecycles,
                platformStateFacade);
        swirldStateManager.setInitialState(consensusState);
        defaultTransactionHandler = new DefaultTransactionHandler(
                platformContext,
                swirldStateManager,
                statusActionSubmitter,
                mock(SoftwareVersion.class),
                platformStateFacade);
    }

    /**
     * @return the {@link DefaultTransactionHandler} used by this tester
     */
    public DefaultTransactionHandler getTransactionHandler() {
        return defaultTransactionHandler;
    }

    /**
     * @return the {@link PlatformStateModifier} used by this tester
     */
    public PlatformStateModifier getPlatformState() {
        return platformState;
    }

    /**
     * @return a list of all {@link PlatformStatusAction}s that have been submitted by the transaction handler
     */
    public List<PlatformStatusAction> getSubmittedActions() {
        return submittedActions;
    }

    /**
     * @return a list of all {@link Round}s that have been provided to the {@link PlatformMerkleStateRoot} for handling
     */
    public List<Round> getHandledRounds() {
        return handledRounds;
    }

    /**
     * @return the {@link SwirldStateManager} used by this tester
     */
    public SwirldStateManager getSwirldStateManager() {
        return swirldStateManager;
    }

    /**
     * @return the {@link StateLifecycles} used by this tester
     */
    public StateLifecycles<PlatformMerkleStateRoot> getStateLifecycles() {
        return stateLifecycles;
    }

    public PlatformStateFacade getPlatformStateFacade() {
        return platformStateFacade;
    }

    public State getConsensusState() {
        return consensusState;
    }
}
