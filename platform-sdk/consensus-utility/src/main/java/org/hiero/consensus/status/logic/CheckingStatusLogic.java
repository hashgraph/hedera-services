// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.status.logic;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import org.hiero.consensus.config.PlatformStatusConfig;
import org.hiero.consensus.model.status.ConsensusLayerStatus;
import org.hiero.consensus.status.actions.FallenBehindAction;
import org.hiero.consensus.status.actions.FreezePeriodEnteredAction;
import org.hiero.consensus.status.actions.SelfEventReachedConsensusAction;
import org.hiero.consensus.status.actions.TimeElapsedAction;

/**
 * Class containing the state machine logic for the {@link ConsensusLayerStatus#CHECKING} status.
 */
public class CheckingStatusLogic extends AbstractStatusLogic {
    /**
     * The platform status config
     */
    private final PlatformStatusConfig config;

    /**
     * Constructor
     *
     * @param config the platform status config
     */
    public CheckingStatusLogic(@NonNull final PlatformStatusConfig config) {
        super(ConsensusLayerStatus.CHECKING);
        this.config = Objects.requireNonNull(config);
    }

    /**
     * {@link ConsensusLayerStatus#CHECKING} status unconditionally transitions to {@link ConsensusLayerStatus#BEHIND} when a
     * {@link FallenBehindAction} is processed.
     */
    @NonNull
    @Override
    protected PlatformStatusLogic onFallenBehind(@NonNull final FallenBehindAction action) {
        return new BehindStatusLogic();
    }

    /**
     * {@link ConsensusLayerStatus#CHECKING} status unconditionally transitions to {@link ConsensusLayerStatus#FREEZING} when a
     * {@link FreezePeriodEnteredAction} is processed.
     */
    @NonNull
    @Override
    protected PlatformStatusLogic onFreezePeriodEntered(@NonNull final FreezePeriodEnteredAction action) {
        return new FreezingStatusLogic(action.freezeRound());
    }

    /**
     * {@link ConsensusLayerStatus#CHECKING} status unconditionally transitions to {@link ConsensusLayerStatus#ACTIVE} when a
     * {@link SelfEventReachedConsensusAction} is processed.
     */
    @NonNull
    @Override
    protected PlatformStatusLogic onSelfEventReachedConsensus(@NonNull final SelfEventReachedConsensusAction action) {
        return new ActiveStatusLogic(action.wallClockTime(), config);
    }

    /**
     * When a {@link TimeElapsedAction} is received while in {@link ConsensusLayerStatus#CHECKING}, the status transitions to
     * {@link ConsensusLayerStatus#ACTIVE} if the platform is currently quiescing, otherwise it remains in
     * {@link ConsensusLayerStatus#CHECKING}.
     */
    @NonNull
    @Override
    protected PlatformStatusLogic onTimeElapsed(@NonNull final TimeElapsedAction action) {
        if (action.quiescingStatus().isQuiescing()) {
            return new ActiveStatusLogic(action.instant(), config);
        }
        return this;
    }
}
