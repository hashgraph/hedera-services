// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.listeners;

import com.swirlds.common.notification.AbstractNotification;
import com.swirlds.platform.state.PlatformMerkleStateRoot;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Class that provides {@link com.swirlds.common.notification.Notification} when state is written to disk
 */
public class StateWriteToDiskCompleteNotification extends AbstractNotification {

    private final long roundNumber;
    private final Instant consensusTimestamp;
    private final boolean isFreezeState;

    public StateWriteToDiskCompleteNotification(
            final long roundNumber, final Instant consensusTimestamp, final boolean isFreezeState) {
        this.roundNumber = roundNumber;
        this.consensusTimestamp = consensusTimestamp;
        this.isFreezeState = isFreezeState;
    }

    /**
     * Gets round number from the state that is written to disk.
     *
     * @return the round number
     */
    public long getRoundNumber() {
        return roundNumber;
    }

    /**
     * Gets the consensus timestamp handled before the state is written to disk.
     *
     * @return the consensus timestamp
     */
    public Instant getConsensusTimestamp() {
        return consensusTimestamp;
    }

    /**
     * Deprecated method, always returns null
     * @return null
     * @deprecated used by PTT for an obsolete feature
     */
    @Deprecated(forRemoval = true)
    public PlatformMerkleStateRoot getState() {
        return null;
    }

    /**
     * Deprecated method, always returns null
     * @return null
     * @deprecated used by PTT for an obsolete feature
     */
    @Deprecated(forRemoval = true)
    public Path getFolder() {
        return null;
    }

    /**
     * Gets whether this is a freeze state
     *
     * @return whether this is a freeze state
     */
    public boolean isFreezeState() {
        return isFreezeState;
    }
}
