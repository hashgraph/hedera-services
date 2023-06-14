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
import com.swirlds.common.system.platformstatus.statusactions.CatastrophicFailureAction;
import com.swirlds.common.system.platformstatus.statusactions.DoneReplayingEventsAction;
import com.swirlds.common.system.platformstatus.statusactions.FallenBehindAction;
import com.swirlds.common.system.platformstatus.statusactions.FreezePeriodEnteredAction;
import com.swirlds.common.system.platformstatus.statusactions.PlatformStatusAction;
import com.swirlds.common.system.platformstatus.statusactions.ReconnectCompleteAction;
import com.swirlds.common.system.platformstatus.statusactions.SelfEventReachedConsensusAction;
import com.swirlds.common.system.platformstatus.statusactions.StartedReplayingEventsAction;
import com.swirlds.common.system.platformstatus.statusactions.StateWrittenToDiskAction;
import com.swirlds.common.system.platformstatus.statusactions.TimeElapsedAction;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Interface representing the state machine logic for an individual {@link PlatformStatus}.
 */
public interface PlatformStatusLogic {
    @NonNull
    PlatformStatusLogic processCatastrophicFailureAction(@NonNull final CatastrophicFailureAction action);

    @NonNull
    PlatformStatusLogic processDoneReplayingEventsAction(@NonNull final DoneReplayingEventsAction action);

    @NonNull
    PlatformStatusLogic processFallenBehindAction(@NonNull final FallenBehindAction action);

    @NonNull
    PlatformStatusLogic processFreezePeriodEnteredAction(@NonNull final FreezePeriodEnteredAction action);

    @NonNull
    PlatformStatusLogic processReconnectCompleteAction(@NonNull final ReconnectCompleteAction action);

    @NonNull
    PlatformStatusLogic processSelfEventReachedConsensusAction(@NonNull final SelfEventReachedConsensusAction action);

    @NonNull
    PlatformStatusLogic processStartedReplayingEventsAction(@NonNull final StartedReplayingEventsAction action);

    @NonNull
    PlatformStatusLogic processStateWrittenToDiskAction(@NonNull final StateWrittenToDiskAction action);

    @NonNull
    PlatformStatusLogic processTimeElapsedAction(@NonNull final TimeElapsedAction action);

    /**
     * Get the status that this logic is for.
     *
     * @return the status that this logic is for
     */
    @NonNull
    PlatformStatus getStatus();

    /**
     * Get the log message to use when an unexpected status action is received.
     *
     * @param action the unexpected status action
     * @return the log message to use when an unexpected status action is received
     */
    default String getUnexpectedStatusActionLog(@NonNull final PlatformStatusAction action) {
        return "Received unexpected status action %s with current status of %s".formatted(action, getStatus());
    }
}
