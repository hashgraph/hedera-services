// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.gossip.shadowgraph;

import com.swirlds.platform.consensus.EventWindow;
import edu.umd.cs.findbugs.annotations.NonNull;

public enum SyncFallenBehindStatus {
    NONE_FALLEN_BEHIND,
    SELF_FALLEN_BEHIND,
    OTHER_FALLEN_BEHIND;

    /**
     * Compute the fallen behind status between ourselves and a peer.
     *
     * @param self  our event window
     * @param other the peer's event window
     * @return the status
     */
    @NonNull
    public static SyncFallenBehindStatus getStatus(@NonNull final EventWindow self, @NonNull final EventWindow other) {
        if (other.getAncientThreshold() < self.getExpiredThreshold()) {
            return OTHER_FALLEN_BEHIND;
        }
        if (self.getAncientThreshold() < other.getExpiredThreshold()) {
            return SELF_FALLEN_BEHIND;
        }
        return NONE_FALLEN_BEHIND;
    }
}
