// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.gossip.impl.gossip.sync.protocol;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.Set;
import org.hiero.consensus.model.status.ConsensusLayerStatus;

/**
 * A utility class for checking if a platform status permits syncing
 */
public class SyncStatusChecker {
    /**
     * The platform statuses that permit syncing. If the platform isn't in one of these statuses, no syncs will be
     * initiated or accepted
     */
    public static final Collection<ConsensusLayerStatus> STATUSES_THAT_PERMIT_SYNC = Set.of(
            ConsensusLayerStatus.ACTIVE,
            ConsensusLayerStatus.FREEZING,
            ConsensusLayerStatus.FREEZE_COMPLETE,
            ConsensusLayerStatus.OBSERVING,
            ConsensusLayerStatus.CHECKING);

    /**
     * Hidden constructor
     */
    private SyncStatusChecker() {}

    /**
     * Determines if the given status permits syncing
     *
     * @param status the status to check
     * @return true if the status permits syncing, false otherwise
     */
    public static boolean doesStatusPermitSync(@NonNull final ConsensusLayerStatus status) {
        return STATUSES_THAT_PERMIT_SYNC.contains(status);
    }
}
