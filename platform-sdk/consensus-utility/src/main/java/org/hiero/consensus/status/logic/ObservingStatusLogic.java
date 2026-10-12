// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.status.logic;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.hiero.consensus.config.PlatformStatusConfig;
import org.hiero.consensus.model.status.ConsensusLayerStatus;
import org.hiero.consensus.status.actions.FallenBehindAction;
import org.hiero.consensus.status.actions.FreezePeriodEnteredAction;
import org.hiero.consensus.status.actions.TimeElapsedAction;

/**
 * Class containing the state machine logic for the {@link ConsensusLayerStatus#OBSERVING} status.
 */
public class ObservingStatusLogic extends AbstractStatusLogic {
    /**
     * The platform status config
     */
    private final PlatformStatusConfig config;

    /**
     * The time at which the platform entered the {@link ConsensusLayerStatus#OBSERVING} status
     */
    private final Instant statusStartTime;

    /**
     * The round number of the freeze period if one has been entered, otherwise null
     */
    private Long freezeRound = null;

    /**
     * Constructor
     *
     * @param statusStartTime the time at which the platform entered the {@link ConsensusLayerStatus#OBSERVING} status
     * @param config          the platform status config
     */
    public ObservingStatusLogic(@NonNull final Instant statusStartTime, @NonNull final PlatformStatusConfig config) {
        super(ConsensusLayerStatus.OBSERVING);
        this.statusStartTime = Objects.requireNonNull(statusStartTime);
        this.config = Objects.requireNonNull(config);
    }

    /**
     * {@link ConsensusLayerStatus#OBSERVING} status unconditionally transitions to {@link ConsensusLayerStatus#BEHIND} when a
     * {@link FallenBehindAction} is processed.
     */
    @NonNull
    @Override
    protected PlatformStatusLogic onFallenBehind(@NonNull final FallenBehindAction action) {
        return new BehindStatusLogic();
    }

    /**
     * Receiving a {@link FreezePeriodEnteredAction} while in {@link ConsensusLayerStatus#OBSERVING} doesn't ever result in a
     * status transition, but this logic method does record the freeze round, which will inform the status progression
     * once the observing period has elapsed.
     */
    @NonNull
    @Override
    protected PlatformStatusLogic onFreezePeriodEntered(@NonNull final FreezePeriodEnteredAction action) {
        validateFreezeRound(freezeRound, action);
        freezeRound = action.freezeRound();
        return this;
    }

    /**
     * {@link ConsensusLayerStatus#OBSERVING} status always transitions to a new status once the observation period has
     * elapsed.
     * <p>
     * The status transitions to {@link ConsensusLayerStatus#FREEZING} if a freeze period was entered during the observation
     * period, otherwise it transitions to {@link ConsensusLayerStatus#CHECKING}.
     */
    @NonNull
    @Override
    protected PlatformStatusLogic onTimeElapsed(@NonNull final TimeElapsedAction action) {
        if (Duration.between(statusStartTime, action.instant()).compareTo(config.observingStatusDelay()) < 0) {
            // if the wait period hasn't elapsed, then stay in this status
            return this;
        }

        if (freezeRound != null) {
            return new FreezingStatusLogic(freezeRound);
        } else {
            return new CheckingStatusLogic(config);
        }
    }
}
