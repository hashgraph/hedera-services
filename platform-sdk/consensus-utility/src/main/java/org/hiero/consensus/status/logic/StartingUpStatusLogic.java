// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.status.logic;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import org.hiero.consensus.config.PlatformStatusConfig;
import org.hiero.consensus.model.status.ConsensusLayerStatus;
import org.hiero.consensus.status.actions.FreezeCompleteAction;
import org.hiero.consensus.status.actions.SelfEventReachedConsensusAction;
import org.hiero.consensus.status.actions.StartedReplayingEventsAction;

/**
 * Class containing the state machine logic for the {@link ConsensusLayerStatus#STARTING_UP} status.
 */
public class StartingUpStatusLogic extends AbstractStatusLogic {
    /**
     * The platform status config
     */
    private final PlatformStatusConfig config;

    /**
     * Constructor
     *
     * @param config the platform status config
     */
    public StartingUpStatusLogic(@NonNull final PlatformStatusConfig config) {
        super(ConsensusLayerStatus.STARTING_UP);
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Receiving a {@link SelfEventReachedConsensusAction} while in {@link ConsensusLayerStatus#STARTING_UP} throws an
     * exception, since this is not conceivable in standard operation.
     */
    @NonNull
    @Override
    protected PlatformStatusLogic onSelfEventReachedConsensus(@NonNull final SelfEventReachedConsensusAction action) {
        return illegal(action);
    }

    /**
     * {@link ConsensusLayerStatus#STARTING_UP} status unconditionally transitions to {@link ConsensusLayerStatus#REPLAYING_EVENTS}
     * when a {@link StartedReplayingEventsAction} is processed.
     */
    @NonNull
    @Override
    protected PlatformStatusLogic onStartedReplayingEvents(@NonNull final StartedReplayingEventsAction action) {
        return new ReplayingEventsStatusLogic(config);
    }

    /**
     * Receiving a {@link FreezeCompleteAction} while in {@link ConsensusLayerStatus#STARTING_UP} throws an exception,
     * since this is not conceivable in standard operation.
     */
    @NonNull
    @Override
    protected PlatformStatusLogic onFreezeComplete(@NonNull final FreezeCompleteAction action) {
        return illegal(action);
    }
}
