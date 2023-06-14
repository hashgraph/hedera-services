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
 * Class containing the state machine logic for the {@link PlatformStatus#RECONNECT_COMPLETE RECONNECT_COMPLETE}
 * status.
 */
public class ReconnectCompleteStatusLogic extends AbstractStatusLogic {
    /**
     * The round number of the reconnect state that was received
     */
    private final long reconnectStateRound;

    /**
     * The round number of the freeze period if one has been entered, otherwise null
     */
    private Long freezeRound = null;

    /**
     * Constructor
     *
     * @param reconnectStateRound the round number of the reconnect state that was received
     * @param config              the platform status config
     */
    public ReconnectCompleteStatusLogic(final long reconnectStateRound, @NonNull final PlatformStatusConfig config) {

        super(config);

        this.reconnectStateRound = reconnectStateRound;
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
        if (action.round() < reconnectStateRound) {
            // if the state written to disk is prior to the reconnect state round, it's old.
            // we need to wait until the reconnected state is written to disk (or a later state)
            return this;
        }

        // always transition to a new status once the reconnect state has been written to disk
        if (freezeRound != null) {
            return new FreezingStatusLogic(freezeRound, getConfig());
        } else {
            return new CheckingStatusLogic(getConfig());
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
        return PlatformStatus.RECONNECT_COMPLETE;
    }
}
