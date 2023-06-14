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
 * Class containing the state machine logic for the {@link PlatformStatus#ACTIVE ACTIVE} status.
 */
public class ActiveStatusLogic extends AbstractStatusLogic {
    /**
     * The last time a self event was observed reaching consensus
     */
    private Instant lastTimeOwnEventReachedConsensus;

    /**
     * Constructor
     *
     * @param config the platform status config
     */
    public ActiveStatusLogic(@NonNull final Instant startTime, @NonNull final PlatformStatusConfig config) {
        super(config);

        // a self event had to reach consensus to arrive at the ACTIVE status
        this.lastTimeOwnEventReachedConsensus = startTime;
    }

    @NonNull
    @Override
    public PlatformStatusLogic processCatastrophicFailureAction(@NonNull CatastrophicFailureAction action) {
        return new CatastrophicFailureStatusLogic(getConfig());
    }

    @NonNull
    @Override
    public PlatformStatusLogic processDoneReplayingEventsAction(@NonNull DoneReplayingEventsAction action) {
        throw new IllegalStateException(getUnexpectedStatusActionLog(action));
    }

    @NonNull
    @Override
    public PlatformStatusLogic processFallenBehindAction(@NonNull FallenBehindAction action) {
        return new BehindStatusLogic(getConfig());
    }

    @NonNull
    @Override
    public PlatformStatusLogic processFreezePeriodEnteredAction(@NonNull FreezePeriodEnteredAction action) {
        return new FreezingStatusLogic(action.freezeRound(), getConfig());
    }

    @NonNull
    @Override
    public PlatformStatusLogic processReconnectCompleteAction(@NonNull ReconnectCompleteAction action) {
        throw new IllegalStateException(getUnexpectedStatusActionLog(action));
    }

    @NonNull
    @Override
    public PlatformStatusLogic processSelfEventReachedConsensusAction(@NonNull SelfEventReachedConsensusAction action) {

        // record the time a self event reached consensus, resetting the timer that would trigger a
        // transition to CHECKING
        lastTimeOwnEventReachedConsensus = action.instant();
        return this;
    }

    @NonNull
    @Override
    public PlatformStatusLogic processStartedReplayingEventsAction(@NonNull StartedReplayingEventsAction action) {
        throw new IllegalStateException(getUnexpectedStatusActionLog(action));
    }

    @NonNull
    @Override
    public PlatformStatusLogic processStateWrittenToDiskAction(@NonNull StateWrittenToDiskAction action) {
        return this;
    }

    @NonNull
    @Override
    public PlatformStatusLogic processTimeElapsedAction(@NonNull TimeElapsedAction action) {
        if (Duration.between(lastTimeOwnEventReachedConsensus, action.instant())
                        .compareTo(getConfig().activeStatusDelay())
                > 0) {
            // if a self event hasn't been observed reaching consensus in the configured duration,
            // go back to CHECKING
            return new CheckingStatusLogic(getConfig());
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
