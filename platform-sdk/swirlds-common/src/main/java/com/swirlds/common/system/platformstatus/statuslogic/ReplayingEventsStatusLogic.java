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

/**
 * Class containing the state machine logic for the {@link PlatformStatus#REPLAYING_EVENTS REPLAYING_EVENTS} status.
 */
public class ReplayingEventsStatusLogic extends AbstractStatusLogic {
    /**
     * The round number of the freeze period if one has been entered, otherwise null
     */
    private Long freezeRound = null;

    /**
     * Constructor
     *
     * @param config the platform status config
     */
    public ReplayingEventsStatusLogic(@NonNull final PlatformStatusConfig config) {
        super(config);
    }

    @NonNull
    @Override
    public PlatformStatusLogic processCatastrophicFailureAction(@NonNull CatastrophicFailureAction action) {
        return new CatastrophicFailureStatusLogic(getConfig());
    }

    @NonNull
    @Override
    public PlatformStatusLogic processDoneReplayingEventsAction(@NonNull DoneReplayingEventsAction action) {
        // always transition to a new status when done replaying events
        if (freezeRound != null) {
            // if a freeze boundary was crossed, we won't transition out of this state until the freeze state
            // has been saved
            return this;
        } else {
            return new ObservingStatusLogic(action.instant(), getConfig());
        }
    }

    @NonNull
    @Override
    public PlatformStatusLogic processFallenBehindAction(@NonNull FallenBehindAction action) {
        throw new IllegalStateException(getUnexpectedStatusActionLog(action));
    }

    @NonNull
    @Override
    public PlatformStatusLogic processFreezePeriodEnteredAction(@NonNull FreezePeriodEnteredAction action) {
        freezeRound = action.freezeRound();
        return this;
    }

    @NonNull
    @Override
    public PlatformStatusLogic processReconnectCompleteAction(@NonNull ReconnectCompleteAction action) {
        throw new IllegalStateException(getUnexpectedStatusActionLog(action));
    }

    @NonNull
    @Override
    public PlatformStatusLogic processSelfEventReachedConsensusAction(@NonNull SelfEventReachedConsensusAction action) {

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
        if (freezeRound != null && action.round() == freezeRound) {
            return new FreezeCompleteStatusLogic(getConfig());
        } else {
            return this;
        }
    }

    @NonNull
    @Override
    public PlatformStatusLogic processTimeElapsedAction(@NonNull TimeElapsedAction action) {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PlatformStatus getStatus() {
        return PlatformStatus.REPLAYING_EVENTS;
    }
}
