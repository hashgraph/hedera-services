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

import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.notification.listeners.PlatformStatusChangeListener;
import com.swirlds.common.notification.listeners.PlatformStatusChangeNotification;
import com.swirlds.common.system.platformstatus.statuslogic.PlatformStatusLogic;
import com.swirlds.common.system.platformstatus.statuslogic.StatusLogicFactory;
import com.swirlds.common.time.Time;
import com.swirlds.logging.LogMarker;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
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

        this.currentStatusLogic = StatusLogicFactory.createStatusLogic(PlatformStatus.STARTING_UP, time, config);
    }

    /**
     * Process a platform status event.
     * <p>
     * Repeated calls of this method cause the platform state machine to be traversed
     *
     * @param event the event to process
     */
    public void processEvent(@NonNull final PlatformStatusEvent event) {
        Objects.requireNonNull(event);

        final PlatformStatus newStatus = currentStatusLogic.processStatusEvent(event);

        // null means status hasn't changed
        if (newStatus == null) {
            return;
        }

        logger.info(
                LogMarker.STARTUP.getMarker(),
                "PlatformStatus changed to {}, after being in {} for {}",
                newStatus,
                currentStatusLogic.getStatus(),
                Duration.between(currentStatusLogic.getStatusStartTime(), time.now()));

        notificationEngine.dispatch(
                PlatformStatusChangeListener.class, new PlatformStatusChangeNotification(newStatus));

        currentStatusLogic = StatusLogicFactory.createStatusLogic(newStatus, time, config);
    }
}
