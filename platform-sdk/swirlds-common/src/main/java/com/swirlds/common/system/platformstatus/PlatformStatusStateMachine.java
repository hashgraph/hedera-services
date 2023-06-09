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

import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.notification.listeners.PlatformStatusChangeListener;
import com.swirlds.common.notification.listeners.PlatformStatusChangeNotification;
import com.swirlds.common.system.platformstatus.statuslogic.PlatformStatusLogic;
import com.swirlds.common.time.Time;
import com.swirlds.logging.payloads.PlatformStatusPayload;
import edu.umd.cs.findbugs.annotations.NonNull;
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
     * The platform status config
     */
    private final PlatformStatusConfig config;

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
        this.config = Objects.requireNonNull(config);
        this.notificationEngine = Objects.requireNonNull(notificationEngine);

        this.currentStatusLogic = PlatformStatus.STARTING_UP.buildLogic();
        this.currentStatusStartTime = time.now();
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

        final PlatformStatus newStatus;
        try {
            newStatus = currentStatusLogic.processStatusAction(action, currentStatusStartTime, time, config);
        } catch (final IllegalStateException e) {
            logger.error(EXCEPTION.getMarker(), e.getMessage(), e);
            return;
        }

        // the status didn't change, so there isn't anything to do
        if (newStatus.equals(currentStatusLogic.getStatus())) {
            return;
        }

        final Instant transitionTime = time.now();

        final String statusChangeMessage =
                "Platform status changed after %s".formatted(Duration.between(currentStatusStartTime, transitionTime));

        logger.info(PLATFORM_STATUS.getMarker(), () -> new PlatformStatusPayload(
                        statusChangeMessage, currentStatusLogic.getStatus().name(), newStatus.name())
                .toString());

        notificationEngine.dispatch(
                PlatformStatusChangeListener.class, new PlatformStatusChangeNotification(newStatus));

        currentStatusStartTime = transitionTime;
        currentStatusLogic = newStatus.buildLogic();
    }
}
