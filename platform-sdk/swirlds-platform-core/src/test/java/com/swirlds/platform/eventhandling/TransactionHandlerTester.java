package com.swirlds.platform.eventhandling;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.status.StatusActionSubmitter;
import com.swirlds.platform.system.status.actions.PlatformStatusAction;
import java.util.ArrayList;
import java.util.List;

public class TransactionHandlerTester {
    private final PlatformState platformState;
    private final DefaultTransactionHandler defaultTransactionHandler;
    private final List<PlatformStatusAction> submittedActions = new ArrayList<>();
    private final List<Round> handledRounds = new ArrayList<>();

    public TransactionHandlerTester(final AddressBook addressBook) {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        platformState = new PlatformState();

        final MerkleRoot consensusState = mock(MerkleRoot.class);
        final SwirldState swirldState = mock(SwirldState.class);
        when(consensusState.getSwirldState()).thenReturn(swirldState);
        when(consensusState.copy()).thenReturn(consensusState);
        when(consensusState.getPlatformState()).thenReturn(platformState);
        doAnswer(i -> {
            handledRounds.add(i.getArgument(0));
            return null;
        }).when(swirldState).handleConsensusRound(any(), any());
        final StatusActionSubmitter statusActionSubmitter = submittedActions::add;
        final SwirldStateManager swirldStateManager = new SwirldStateManager(
                platformContext,
                addressBook,
                NodeId.FIRST_NODE_ID,
                statusActionSubmitter,
                new BasicSoftwareVersion(1)
        );
        swirldStateManager.setInitialState(consensusState);
        defaultTransactionHandler = new DefaultTransactionHandler(
                platformContext, swirldStateManager, statusActionSubmitter, mock(SoftwareVersion.class));
    }

    public DefaultTransactionHandler getTransactionHandler() {
        return defaultTransactionHandler;
    }

    public PlatformState getPlatformState() {
        return platformState;
    }

    public List<PlatformStatusAction> getSubmittedActions() {
        return submittedActions;
    }

    public List<Round> getHandledRounds() {
        return handledRounds;
    }
}
