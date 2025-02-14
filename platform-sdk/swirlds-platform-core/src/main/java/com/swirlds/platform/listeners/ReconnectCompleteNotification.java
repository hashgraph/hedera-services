// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.listeners;

import com.swirlds.common.notification.AbstractNotification;
import com.swirlds.platform.state.PlatformMerkleStateRoot;
import java.time.Instant;

/**
 * Class that provides {@link com.swirlds.common.notification.Notification} when reconnect completes
 */
public class ReconnectCompleteNotification extends AbstractNotification {

    private long roundNumber;
    private Instant consensusTimestamp;
    private PlatformMerkleStateRoot state;

    public ReconnectCompleteNotification(
            final long roundNumber, final Instant consensusTimestamp, final PlatformMerkleStateRoot state) {
        this.roundNumber = roundNumber;
        this.consensusTimestamp = consensusTimestamp;
        this.state = state;
    }

    /**
     * get round number from the {@link PlatformMerkleStateRoot}
     *
     * @return round number
     */
    public long getRoundNumber() {
        return roundNumber;
    }

    /**
     * The last consensus timestamp handled before the state was signed to the callback
     *
     * @return last consensus timestamp
     */
    public Instant getConsensusTimestamp() {
        return consensusTimestamp;
    }

    /**
     * get the {@link PlatformMerkleStateRoot} instance
     *
     * @return PlatformMerkleStateRoot
     */
    public PlatformMerkleStateRoot getState() {
        return state;
    }
}
