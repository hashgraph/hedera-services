/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.logic;

import static com.swirlds.common.system.PlatformStatus.ACTIVE;
import static com.swirlds.common.system.PlatformStatus.FREEZE_COMPLETE;

import com.hedera.services.context.CurrentPlatformStatus;
import com.hedera.services.stream.RecordStreamManager;
import com.swirlds.common.notification.listeners.PlatformStatusChangeListener;
import com.swirlds.common.notification.listeners.PlatformStatusChangeNotification;
import com.swirlds.common.system.NodeId;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Listener that will be notified with {@link
 * com.swirlds.common.notification.listeners.PlatformStatusChangeListener} when platform status
 * changes. This will set {@link RecordStreamManager}'s {@code inFreeze} status
 */
@Singleton
public class StatusChangeListener implements PlatformStatusChangeListener {
    private static final Logger log = LogManager.getLogger(StatusChangeListener.class);
    private final CurrentPlatformStatus currentPlatformStatus;
    private final NodeId nodeId;
    private final RecordStreamManager recordStreamManager;

    @Inject
    public StatusChangeListener(
            final CurrentPlatformStatus currentPlatformStatus,
            final NodeId nodeId,
            final RecordStreamManager recordStreamManager) {
        this.currentPlatformStatus = currentPlatformStatus;
        this.nodeId = nodeId;
        this.recordStreamManager = recordStreamManager;
    }

    @Override
    public void notify(PlatformStatusChangeNotification notification) {
        log.info(
                "Notification Received: Current Platform status changed to {}",
                notification.getNewStatus());

        final var status = notification.getNewStatus();
        currentPlatformStatus.set(status);

        log.info("Now current platform status = {} in HederaNode#{}.", status, nodeId);
        if (status == ACTIVE) {
            recordStreamManager.setInFreeze(false);
        } else if (status == FREEZE_COMPLETE) {
            recordStreamManager.setInFreeze(true);
        } else {
            log.info("Platform {} status set to : {}", nodeId, status);
        }
    }
}
