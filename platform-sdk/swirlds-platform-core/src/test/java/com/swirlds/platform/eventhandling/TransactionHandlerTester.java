package com.swirlds.platform.eventhandling;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.status.StatusActionSubmitter;
import com.swirlds.platform.system.status.actions.PlatformStatusAction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

public class TransactionHandlerTester {

    final SwirldStateManager swirldStateManager;
    final PlatformState platformState;
    final DefaultTransactionHandler defaultTransactionHandler;
    private final List<PlatformStatusAction> submittedActions = new ArrayList<>();

    public TransactionHandlerTester() {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        platformState = mock(PlatformState.class);

        final MerkleRoot consensusState = mock(State.class);
        final MerkleRoot stateForSigning = mock(State.class);
        when(consensusState.getPlatformState()).thenReturn(platformState);
        swirldStateManager = mock(SwirldStateManager.class);
        when(swirldStateManager.getConsensusState()).thenReturn(consensusState);
        when(swirldStateManager.getStateForSigning()).thenReturn(stateForSigning);

        final StatusActionSubmitter statusActionSubmitter = submittedActions::add;

        defaultTransactionHandler = new DefaultTransactionHandler(
                platformContext, swirldStateManager, statusActionSubmitter, mock(SoftwareVersion.class));
    }

    public DefaultTransactionHandler getTransactionHandler() {
        return defaultTransactionHandler;
    }

    public List<PlatformStatusAction> getSubmittedActions() {
        return submittedActions;
    }

    public void verifyRoundHandled(final ConsensusRound consensusRound){
        verify(swirldStateManager).handleConsensusRound(consensusRound);
        try {
            verify(platformState)
                    .setLegacyRunningEventHash(consensusRound
                            .getStreamedEvents()
                            .getLast()
                            .getRunningHash()
                            .getFutureHash()
                            .getAndRethrow());
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void verifyFreezeRound(){
        verify(swirldStateManager, never()).savedStateInFreezePeriod();
    }
}
