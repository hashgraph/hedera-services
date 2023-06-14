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
 * Class containing the state machine logic for the {@link PlatformStatus#CATASTROPHIC_FAILURE CATASTROPHIC_FAILURE}
 * status.
 */
public class CatastrophicFailureStatusLogic extends AbstractStatusLogic {
    /**
     * Constructor
     *
     * @param config the platform status config
     */
    public CatastrophicFailureStatusLogic(@NonNull final PlatformStatusConfig config) {
        super(config);
    }

    @NonNull
    @Override
    public PlatformStatusLogic processCatastrophicFailureAction(@NonNull CatastrophicFailureAction action) {
        return this;
    }

    @NonNull
    @Override
    public PlatformStatusLogic processDoneReplayingEventsAction(@NonNull DoneReplayingEventsAction action) {
        return this;
    }

    @NonNull
    @Override
    public PlatformStatusLogic processFallenBehindAction(@NonNull FallenBehindAction action) {
        return this;
    }

    @NonNull
    @Override
    public PlatformStatusLogic processFreezePeriodEnteredAction(@NonNull FreezePeriodEnteredAction action) {
        return this;
    }

    @NonNull
    @Override
    public PlatformStatusLogic processReconnectCompleteAction(@NonNull ReconnectCompleteAction action) {
        return this;
    }

    @NonNull
    @Override
    public PlatformStatusLogic processSelfEventReachedConsensusAction(@NonNull SelfEventReachedConsensusAction action) {

        return this;
    }

    @NonNull
    @Override
    public PlatformStatusLogic processStartedReplayingEventsAction(@NonNull StartedReplayingEventsAction action) {
        return this;
    }

    @NonNull
    @Override
    public PlatformStatusLogic processStateWrittenToDiskAction(@NonNull StateWrittenToDiskAction action) {
        return this;
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
        return PlatformStatus.CATASTROPHIC_FAILURE;
    }
}
