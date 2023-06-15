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

package com.swirlds.common.system.platformstatus;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.PLATFORM_STATUS;

import com.swirlds.base.time.Time;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.notification.listeners.PlatformStatusChangeListener;
import com.swirlds.common.notification.listeners.PlatformStatusChangeNotification;
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
import com.swirlds.common.system.platformstatus.statuslogic.PlatformStatusLogic;
import com.swirlds.common.system.platformstatus.statuslogic.StartingUpStatusLogic;
import com.swirlds.logging.payloads.PlatformStatusPayload;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The platform status state machine
 */
public class PlatformStatusStateMachine {
    private static final Logger logger = LogManager.getLogger(PlatformStatusStateMachine.class);

    /**
     * A source of time
     */
    private final Time time;

    /**
     * For passing notifications between the platform and the application.
     */
    private final NotificationEngine notificationEngine;

    /**
     * The object containing the state machine logic for the current status
     */
    private PlatformStatusLogic currentStatusLogic;

    /**
     * The time at which the current status started
     */
    private Instant currentStatusStartTime;

    /**
     * Constructor
     *
     * @param time               a source of time
     * @param config             the platform status config
     * @param notificationEngine the notification engine
     */
    public PlatformStatusStateMachine(
            @NonNull final Time time,
            @NonNull PlatformStatusConfig config,
            @NonNull final NotificationEngine notificationEngine) {

        this.time = Objects.requireNonNull(time);
        this.notificationEngine = Objects.requireNonNull(notificationEngine);

        this.currentStatusLogic = new StartingUpStatusLogic(config);
        this.currentStatusStartTime = time.now();
    }

    @Nullable
    private PlatformStatusLogic getNewLogic(@NonNull final PlatformStatusAction action) {
        Objects.requireNonNull(action);

        try {
            if (action instanceof CatastrophicFailureAction catastrophicFailureAction) {
                return currentStatusLogic.processCatastrophicFailureAction(catastrophicFailureAction);
            } else if (action instanceof DoneReplayingEventsAction doneReplayingEventsAction) {
                return currentStatusLogic.processDoneReplayingEventsAction(doneReplayingEventsAction);
            } else if (action instanceof FallenBehindAction fallenBehindAction) {
                return currentStatusLogic.processFallenBehindAction(fallenBehindAction);
            } else if (action instanceof FreezePeriodEnteredAction freezePeriodEnteredAction) {
                return currentStatusLogic.processFreezePeriodEnteredAction(freezePeriodEnteredAction);
            } else if (action instanceof ReconnectCompleteAction reconnectCompleteAction) {
                return currentStatusLogic.processReconnectCompleteAction(reconnectCompleteAction);
            } else if (action instanceof SelfEventReachedConsensusAction selfEventReachedConsensusAction) {
                return currentStatusLogic.processSelfEventReachedConsensusAction(selfEventReachedConsensusAction);
            } else if (action instanceof StartedReplayingEventsAction startedReplayingEventsAction) {
                return currentStatusLogic.processStartedReplayingEventsAction(startedReplayingEventsAction);
            } else if (action instanceof StateWrittenToDiskAction stateWrittenToDiskAction) {
                return currentStatusLogic.processStateWrittenToDiskAction(stateWrittenToDiskAction);
            } else if (action instanceof TimeElapsedAction timeElapsedAction) {
                return currentStatusLogic.processTimeElapsedAction(timeElapsedAction);
            } else {
                throw new IllegalArgumentException(
                        "Unknown action type: " + action.getClass().getName());
            }
        } catch (final IllegalPlatformStatusException e) {
            logger.error(EXCEPTION.getMarker(), e.getMessage());
            return null;
        }
    }

    /**
     * Process a platform status action.
     * <p>
     * Repeated calls of this method cause the platform state machine to be traversed
     *
     * @param action the action to process
     */
    public void processStatusAction(@NonNull final PlatformStatusAction action) {
        Objects.requireNonNull(action);

        final PlatformStatusLogic newLogic = getNewLogic(action);

        if (newLogic == null || newLogic == currentStatusLogic) {
            // if status didn't change, there isn't anything to do
            return;
        }

        final String previousStatusName = currentStatusLogic.getStatus().name();
        final String newStatusName = newLogic.getStatus().name();

        final String statusChangeMessage = "Platform spent %s time in %s. Now in %s"
                .formatted(Duration.between(currentStatusStartTime, time.now()), previousStatusName, newStatusName);

        logger.info(
                PLATFORM_STATUS.getMarker(),
                () -> new PlatformStatusPayload(statusChangeMessage, previousStatusName, newStatusName).toString());

        notificationEngine.dispatch(
                PlatformStatusChangeListener.class, new PlatformStatusChangeNotification(newLogic.getStatus()));

        currentStatusLogic = newLogic;
        currentStatusStartTime = time.now();
    }

    /**
     * Get the current platform status
     *
     * @return the current platform status
     */
    public PlatformStatus getCurrentStatus() {
        return currentStatusLogic.getStatus();
    }
}
