package com.swirlds.platform.eventhandling;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.status.StatusActionSubmitter;
import com.swirlds.platform.system.status.actions.PlatformStatusAction;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;

public class TransactionHandlerTester {

    final AddressBook addressBook;
    final SwirldStateManager swirldStateManager;
    final SwirldState swirldState;
    final PlatformState platformState;
    final DefaultTransactionHandler defaultTransactionHandler;
    private final List<PlatformStatusAction> submittedActions = new ArrayList<>();

    public TransactionHandlerTester(final AddressBook addressBook) {
        this.addressBook = addressBook;
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        //platformState = mock(PlatformState.class);
        platformState = new PlatformState();

        final MerkleRoot consensusState = mock(State.class);
        swirldState = mock(SwirldState.class);
        when(consensusState.getSwirldState()).thenReturn(swirldState);
        //final MerkleRoot stateForSigning = mock(State.class);
        when(consensusState.copy()).thenReturn(consensusState);
        when(consensusState.getPlatformState()).thenReturn(platformState);
        //when(stateForSigning.getPlatformState()).thenReturn(platformState);
        //when(platformState.getConsensusTimestamp()).thenReturn(randotron.nextInstant());
        final StatusActionSubmitter statusActionSubmitter = submittedActions::add;
        swirldStateManager = new SwirldStateManager(
                platformContext,
                addressBook,
                NodeId.FIRST_NODE_ID,
                statusActionSubmitter,
                new BasicSoftwareVersion(1)
        );
        swirldStateManager.setInitialState(consensusState);
//        swirldStateManager = mock(SwirldStateManager.class);
//        when(swirldStateManager.getConsensusState()).thenReturn(consensusState);
//        when(swirldStateManager.getStateForSigning()).thenReturn(stateForSigning);



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
        verify(swirldState).handleConsensusRound(consensusRound, platformState);

        try {
//            verify(platformState)
//                    .setLegacyRunningEventHash(consensusRound
//                            .getStreamedEvents()
//                            .getLast()
//                            .getRunningHash()
//                            .getFutureHash()
//                            .getAndRethrow());
            Assertions.assertEquals(
                    platformState.getLegacyRunningEventHash(),
                    consensusRound
                            .getStreamedEvents()
                            .getLast()
                            .getRunningHash()
                            .getFutureHash()
                            .getAndRethrow()
            );
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void verifyFrozen(final boolean frozen){
        if(frozen){
            Assertions.assertNotNull(platformState.getLastFrozenTime());
        }else{
            Assertions.assertNull(platformState.getLastFrozenTime());
        }
    }
}
