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

package com.swirlds.common.system.platformstatus.statuslogic;

import com.swirlds.common.system.platformstatus.IllegalPlatformStatusException;
import com.swirlds.common.system.platformstatus.PlatformStatus;
import com.swirlds.common.system.platformstatus.PlatformStatusConfig;
import com.swirlds.common.system.platformstatus.statusactions.CatastrophicFailureAction;
import com.swirlds.common.system.platformstatus.statusactions.DoneReplayingEventsAction;
import com.swirlds.common.system.platformstatus.statusactions.FallenBehindAction;
import com.swirlds.common.system.platformstatus.statusactions.FreezePeriodEnteredAction;
import com.swirlds.common.system.platformstatus.statusactions.ReconnectCompleteAction;
import com.swirlds.common.system.platformstatus.statusactions.SelfEventReachedConsensusAction;
import com.swirlds.common.system.platformstatus.statusactions.StartedReplayingEventsAction;
import com.swirlds.common.system.platformstatus.statusactions.StateWrittenToDiskAction;
import com.swirlds.common.system.platformstatus.statusactions.TimeElapsedAction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;

/**
 * Class containing the state machine logic for the {@link PlatformStatus#ACTIVE} status.
 */
public class ActiveStatusLogic implements PlatformStatusLogic {
    /**
     * The platform status config
     */
    private final PlatformStatusConfig config;

    /**
     * The last time a self event was observed reaching consensus
     */
    private Instant lastTimeOwnEventReachedConsensus;

    /**
     * Constructor
     *
     * @param startTime the time when the platform transitioned to the {@link PlatformStatus#ACTIVE} status
     * @param config    the platform status config
     */
    public ActiveStatusLogic(@NonNull final Instant startTime, @NonNull final PlatformStatusConfig config) {
        // a self event had to reach consensus to arrive at the ACTIVE status
        this.lastTimeOwnEventReachedConsensus = startTime;
        this.config = config;
    }

    /**
     * {@inheritDoc}
     * <p>
     * {@link PlatformStatus#ACTIVE} status unconditionally transitions to {@link PlatformStatus#CATASTROPHIC_FAILURE}
     * when a {@link CatastrophicFailureAction} is processed.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processCatastrophicFailureAction(@NonNull final CatastrophicFailureAction action) {
        return new CatastrophicFailureStatusLogic();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Receiving a {@link DoneReplayingEventsAction} while in {@link PlatformStatus#ACTIVE} throws an exception, since
     * this is not conceivable in standard operation.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processDoneReplayingEventsAction(@NonNull final DoneReplayingEventsAction action) {
        throw new IllegalPlatformStatusException(action, getStatus());
    }

    /**
     * {@inheritDoc}
     * <p>
     * {@link PlatformStatus#ACTIVE} status unconditionally transitions to {@link PlatformStatus#BEHIND} when a
     * {@link FallenBehindAction} is processed.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processFallenBehindAction(@NonNull final FallenBehindAction action) {
        return new BehindStatusLogic(config);
    }

    /**
     * {@inheritDoc}
     * <p>
     * {@link PlatformStatus#ACTIVE} status unconditionally transitions to {@link PlatformStatus#FREEZING} when a
     * {@link FreezePeriodEnteredAction} is processed.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processFreezePeriodEnteredAction(@NonNull final FreezePeriodEnteredAction action) {
        return new FreezingStatusLogic(action.freezeRound());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Receiving a {@link ReconnectCompleteAction} while in {@link PlatformStatus#ACTIVE} throws an exception, since
     * this is not conceivable in standard operation.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processReconnectCompleteAction(@NonNull final ReconnectCompleteAction action) {
        throw new IllegalPlatformStatusException(action, getStatus());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Receiving a {@link SelfEventReachedConsensusAction} while in {@link PlatformStatus#ACTIVE} doesn't ever result in
     * a status transition, but this logic method does record the time the event reached consensus.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processSelfEventReachedConsensusAction(
            @NonNull final SelfEventReachedConsensusAction action) {
        lastTimeOwnEventReachedConsensus = action.instant();
        return this;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Receiving a {@link StartedReplayingEventsAction} while in {@link PlatformStatus#ACTIVE} throws an exception,
     * since this is not conceivable in standard operation.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processStartedReplayingEventsAction(@NonNull final StartedReplayingEventsAction action) {
        throw new IllegalPlatformStatusException(action, getStatus());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Receiving a {@link StateWrittenToDiskAction} while in {@link PlatformStatus#ACTIVE} has no effect on the state
     * machine.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processStateWrittenToDiskAction(@NonNull final StateWrittenToDiskAction action) {
        return this;
    }

    /**
     * {@inheritDoc}
     * <p>
     * When a {@link TimeElapsedAction} is received while in {@link PlatformStatus#ACTIVE}, we must evaluate whether too
     * much time has elapsed since seeing a self event reach consensus. If too much time has elapsed, the status
     * transitions to {@link PlatformStatus#CHECKING}. Otherwise, the status remains {@link PlatformStatus#ACTIVE}.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processTimeElapsedAction(@NonNull final TimeElapsedAction action) {
        if (Duration.between(lastTimeOwnEventReachedConsensus, action.instant()).compareTo(config.activeStatusDelay())
                > 0) {
            return new CheckingStatusLogic(config);
        } else {
            return this;
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PlatformStatus getStatus() {
        return PlatformStatus.ACTIVE;
    }
}
