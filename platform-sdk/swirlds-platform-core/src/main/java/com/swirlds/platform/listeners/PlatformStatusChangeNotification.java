// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.listeners;

import org.hiero.consensus.model.notification.AbstractNotification;
import org.hiero.consensus.model.status.ConsensusLayerStatus;

/**
 * This notification is sent when the platform status changes.
 */
public class PlatformStatusChangeNotification extends AbstractNotification {

    private final ConsensusLayerStatus newStatus;

    /**
     * Create a new platform status change notification.
     *
     * @param newStatus
     * 		the new status of the platform
     */
    public PlatformStatusChangeNotification(final ConsensusLayerStatus newStatus) {
        this.newStatus = newStatus;
    }

    /**
     * Get the new platform status.
     *
     * @return the new platform status
     */
    public ConsensusLayerStatus getNewStatus() {
        return newStatus;
    }
}
