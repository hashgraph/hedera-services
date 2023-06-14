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
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Class containing the state machine logic for the {@link PlatformStatus#RECONNECT_COMPLETE} status.
 */
public class ReconnectCompleteStatusLogic implements PlatformStatusLogic {
    /**
     * The platform status config
     */
    private final PlatformStatusConfig config;

    /**
     * The round number of the reconnect state that was received
     */
    private final long reconnectStateRound;

    /**
     * The round number of the freeze period if one has been entered, otherwise null
     */
    private Long freezeRound;

    /**
     * Constructor
     *
     * @param reconnectStateRound the round number of the reconnect state that was received
     * @param freezeRound         the round number of the freeze period if one has been entered, otherwise null
     * @param config              the platform status config
     */
    public ReconnectCompleteStatusLogic(
            final long reconnectStateRound,
            final @Nullable Long freezeRound,
            @NonNull final PlatformStatusConfig config) {
        // TODO write tests where freeze round is both null and not null
        this.reconnectStateRound = reconnectStateRound;
        this.freezeRound = freezeRound;
        this.config = config;
    }

    /**
     * {@inheritDoc}
     * <p>
     * {@link PlatformStatus#RECONNECT_COMPLETE} status unconditionally transitions to
     * {@link PlatformStatus#CATASTROPHIC_FAILURE} when a {@link CatastrophicFailureAction} is processed.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processCatastrophicFailureAction(@NonNull CatastrophicFailureAction action) {
        return new CatastrophicFailureStatusLogic();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Receiving a {@link DoneReplayingEventsAction} while in {@link PlatformStatus#RECONNECT_COMPLETE} throws an
     * exception, since this is not conceivable in standard operation.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processDoneReplayingEventsAction(@NonNull DoneReplayingEventsAction action) {
        throw new IllegalStateException(getUnexpectedStatusActionLog(action));
    }

    /**
     * {@inheritDoc}
     * <p>
     * {@link PlatformStatus#RECONNECT_COMPLETE} status unconditionally transitions to {@link PlatformStatus#BEHIND}
     * when a {@link FallenBehindAction} is processed.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processFallenBehindAction(@NonNull FallenBehindAction action) {
        return new BehindStatusLogic(config);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Receiving a {@link FreezePeriodEnteredAction} while in {@link PlatformStatus#RECONNECT_COMPLETE} doesn't ever
     * result in a status transition, but this logic method does record the freeze round, which will inform the status
     * progression once the reconnect state has been saved.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processFreezePeriodEnteredAction(@NonNull FreezePeriodEnteredAction action) {
        freezeRound = action.freezeRound();
        return this;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Receiving a {@link ReconnectCompleteAction} while in {@link PlatformStatus#RECONNECT_COMPLETE} throws an
     * exception, since this is not conceivable in standard operation.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processReconnectCompleteAction(@NonNull ReconnectCompleteAction action) {
        throw new IllegalStateException(getUnexpectedStatusActionLog(action));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Receiving a {@link SelfEventReachedConsensusAction} while in {@link PlatformStatus#RECONNECT_COMPLETE} has no
     * effect on the state machine.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processSelfEventReachedConsensusAction(@NonNull SelfEventReachedConsensusAction action) {
        return this;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Receiving a {@link StartedReplayingEventsAction} while in {@link PlatformStatus#RECONNECT_COMPLETE} throws an
     * exception, since this is not conceivable in standard operation.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processStartedReplayingEventsAction(@NonNull StartedReplayingEventsAction action) {
        throw new IllegalStateException(getUnexpectedStatusActionLog(action));
    }

    /**
     * {@inheritDoc}
     * <p>
     * If the state written to disk is prior to the reconnect state round, it's old, so we need to wait until the
     * reconnected state is written to disk (or a later state).
     * <p>
     * If the state written to disk is the reconnected state or later, then we can transition to a new status. If a
     * freeze boundary has been crossed, we transition to {@link PlatformStatus#FREEZING} status. Otherwise, we
     * transition to {@link PlatformStatus#CHECKING} status.
     */
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
            return new FreezingStatusLogic(freezeRound);
        } else {
            return new CheckingStatusLogic(config);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Receiving a {@link TimeElapsedAction} while in {@link PlatformStatus#RECONNECT_COMPLETE} has no effect on the
     * state machine.
     */
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
