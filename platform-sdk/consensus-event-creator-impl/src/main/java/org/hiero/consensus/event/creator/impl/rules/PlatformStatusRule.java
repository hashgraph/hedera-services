// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.event.creator.impl.rules;

import static org.hiero.consensus.event.creator.impl.EventCreationStatus.PLATFORM_STATUS;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.hiero.consensus.event.creator.impl.EventCreationStatus;
import org.hiero.consensus.model.status.ConsensusLayerStatus;

/**
 * Limits the creation of new events depending on the current platform status.
 */
public class PlatformStatusRule implements EventCreationRule {

    /**
     * The current platform status.
     */
    private ConsensusLayerStatus consensusLayerStatus;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEventCreationPermitted() {
        final ConsensusLayerStatus currentStatus = this.consensusLayerStatus;

        return currentStatus == ConsensusLayerStatus.ACTIVE
                || currentStatus == ConsensusLayerStatus.CHECKING
                || currentStatus == ConsensusLayerStatus.FREEZING;
    }

    public void setPlatformStatus(final ConsensusLayerStatus consensusLayerStatus) {
        this.consensusLayerStatus = consensusLayerStatus;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void eventWasCreated() {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public EventCreationStatus getEventCreationStatus() {
        return PLATFORM_STATUS;
    }
}
