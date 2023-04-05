/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.reconnect;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.SOCKET_EXCEPTIONS;
import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.common.StartupTime;
import com.swirlds.common.merkle.synchronization.settings.ReconnectSettings;
import com.swirlds.common.system.NodeId;
import com.swirlds.logging.payloads.ReconnectFailurePayload;
import com.swirlds.logging.payloads.UnableToReconnectPayload;
import com.swirlds.platform.Connection;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.network.NetworkUtils;
import com.swirlds.platform.system.SystemExitReason;
import com.swirlds.platform.system.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Throttles reconnect learner attempts
 * <p>
 * NOTE: This class is not thread safe
 */
public class ReconnectLearnerThrottle {
    private static final Logger logger = LogManager.getLogger(ReconnectLearnerThrottle.class);
    private final NodeId selfId;
    private final ReconnectSettings settings;
    /** The number of times reconnect has failed since the last succesfull reconnect. */
    private int failedReconnectsInARow;

    public ReconnectLearnerThrottle(final NodeId selfId, final ReconnectSettings settings) {
        this.selfId = selfId;
        this.settings = settings;
        this.failedReconnectsInARow = 0;
    }

    /**
     * Notifies the throttle that a successful reconnect occurred
     */
    public void successfulReconnect() {
        failedReconnectsInARow = 0;
    }

    /**
     * Notifies the throttle that a reconnect failed
     */
    public void handleFailedReconnect(final Connection conn, final Exception e) {
        if (Utilities.isOrCausedBySocketException(e)) {
            logger.error(SOCKET_EXCEPTIONS.getMarker(), () -> new ReconnectFailurePayload(
                            "Got socket exception while receiving a signed state! " + NetworkUtils.formatException(e),
                            ReconnectFailurePayload.CauseOfFailure.SOCKET)
                    .toString());
        } else {
            logger.error(
                    EXCEPTION.getMarker(),
                    () -> new ReconnectFailurePayload(
                                    "Error while receiving a signed state!",
                                    ReconnectFailurePayload.CauseOfFailure.ERROR)
                            .toString(),
                    e);
            if (conn != null) {
                conn.disconnect();
            }
        }

        failedReconnectsInARow++;
        killNodeIfThresholdMet();
    }

    private void killNodeIfThresholdMet() {
        if (failedReconnectsInARow >= settings.getMaximumReconnectFailuresBeforeShutdown()) {
            logger.error(EXCEPTION.getMarker(), "Too many reconnect failures in a row, killing node");
            SystemUtils.exitSystem(SystemExitReason.RECONNECT_FAILURE);
        }
    }

    /**
     * Check if a reconnect is currently allowed. If not then kill the node.
     */
    public void exitIfReconnectIsDisabled() {
        if (!settings.isActive()) {
            logger.warn(STARTUP.getMarker(), () -> new UnableToReconnectPayload(
                            "Node has fallen behind, reconnect is disabled, will die", selfId.getIdAsInt())
                    .toString());
            SystemUtils.exitSystem(SystemExitReason.BEHIND_RECONNECT_DISABLED);
        }

        if (settings.getReconnectWindowSeconds() >= 0
                && settings.getReconnectWindowSeconds()
                        < StartupTime.getTimeSinceStartup().toSeconds()) {
            logger.warn(STARTUP.getMarker(), () -> new UnableToReconnectPayload(
                            "Node has fallen behind, reconnect is disabled outside of time window, will die",
                            selfId.getIdAsInt())
                    .toString());
            SystemUtils.exitSystem(SystemExitReason.BEHIND_RECONNECT_DISABLED);
        }
    }
}
