// SPDX-License-Identifier: Apache-2.0
package org.hiero.sloth.fixtures.result;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.hiero.consensus.main.model.NodeId;
import org.hiero.consensus.model.status.ConsensusLayerStatus;

/**
 * Defines a subscriber that will receive each {@link ConsensusLayerStatus} a node enters.
 */
@FunctionalInterface
public interface PlatformStatusSubscriber {

    /**
     * Called when the node enters a new {@link ConsensusLayerStatus}.
     *
     * @param nodeId the node that created the round
     * @param status the new platform status
     * @return {@link SubscriberAction#UNSUBSCRIBE} to unsubscribe, {@link SubscriberAction#CONTINUE} to continue
     */
    SubscriberAction onPlatformStatusChange(@NonNull NodeId nodeId, @NonNull ConsensusLayerStatus status);
}
