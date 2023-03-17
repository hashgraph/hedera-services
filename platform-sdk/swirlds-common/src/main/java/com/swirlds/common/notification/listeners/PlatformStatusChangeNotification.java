/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.notification.listeners;

import com.swirlds.common.notification.AbstractNotification;
import com.swirlds.common.system.PlatformStatus;

/**
 * This notification is sent when the platform status changes.
 */
public class PlatformStatusChangeNotification extends AbstractNotification {

    private final PlatformStatus newStatus;

    /**
     * Create a new platform status change notification.
     *
     * @param newStatus
     * 		the new status of the platform
     */
    public PlatformStatusChangeNotification(final PlatformStatus newStatus) {
        this.newStatus = newStatus;
    }

    /**
     * Get the new platform status.
     *
     * @return the new platform status
     */
    public PlatformStatus getNewStatus() {
        return newStatus;
    }
}
