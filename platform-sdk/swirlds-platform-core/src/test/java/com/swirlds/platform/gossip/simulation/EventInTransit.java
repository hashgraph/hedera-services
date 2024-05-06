package com.swirlds.platform.gossip.simulation;

import com.hedera.hapi.platform.event.GossipEvent;
import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * An event that is in transit between nodes in the network.
 * @param event the event being transmitted
 * @param sender the node that sent the event
 * @param arrivalTime the time the event is scheduled to arrive at its destination
 */
public record EventInTransit(@NonNull GossipEvent event, @NonNull NodeId sender, @NonNull Instant arrivalTime) {
}
