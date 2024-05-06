package com.swirlds.platform.gossip.simulation;

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.wiring.components.Gossip;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Connects {@link SimulatedGossip} peers in a simulated network.
 */
public class SimulatedNetwork {

    /**
     * Events that have been submitted within the most recent tick.
     */
    private final Map<NodeId, List<Gossip>> newlySubmittedEvents = new HashMap<>();



    /**
     * Constructor.
     */
    public SimulatedNetwork(@NonNull final AddressBook addressBook) {

    }

    /**
     * Submit an event to be gossiped around the network.
     *
     * @param event the event to gossip
     */
    public void submitEvent(@NonNull final Gossip event) {

    }

    /**
     * Move time forward to the given instant.
     *
     * @param now the new time
     */
    public void tick(@NonNull final Instant now) {

    }

}
