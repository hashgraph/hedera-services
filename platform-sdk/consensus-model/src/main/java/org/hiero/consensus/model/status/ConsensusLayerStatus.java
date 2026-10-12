// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.model.status;

import org.hiero.base.UniqueId;

/**
 * The status of the consensus layer
 */
public enum ConsensusLayerStatus implements UniqueId {
    /**
     * The consensus layer is starting up.
     */
    STARTING_UP(1),
    /**
     * The consensus layer is actively trying to produce the next consensus round. Gossip and event creation are permitted.
     */
    ACTIVE(2),
    /**
     * The consensus layer is far enough behind its peers to require a reconnect.
     */
    BEHIND(4),
    /**
     * A freeze timestamp has been crossed, and the consensus layer is in the process of freezing.
     */
    FREEZING(5),
    /**
     * The consensus node has completed the freeze.
     */
    FREEZE_COMPLETE(6),
    /**
     * The consensus layer is replaying events from the pre-consensus event stream. Gossip is not permitted.
     */
    REPLAYING_EVENTS(7),
    /**
     * The consensus layer has just started, and is observing the network. The consensus layer may gossip, but not create events.
     */
    OBSERVING(8),
    /**
     * The consensus layer has started up or has finished reconnecting, and is now ready to join the network. It may
     * be gossiping and creating empty events, but not yet accepting app transactions.
     * TODO come back to this. Do we need an IDLE status?
     */
    CHECKING(9),
    /**
     * The consensus layer has encountered a failure, and is unable to continue. The consensus layer is idle.
     */
    CATASTROPHIC_FAILURE(11);


    // TODO Consider adding a new "IDLE" status that kicks in if a new round has not been requested by the
    // Execution layer in a configurable amount of wall clock time. The consensus layer can only report CHECKING/ACTIVE
    // type status if it is creating events and reaching consensus, which it cannot do if the Execution layer
    // is not requesting new rounds.

    /**
     * Unique ID of the enum value
     */
    private final int id;

    /**
     * Constructs an enum instance
     *
     * @param id unique ID of the instance
     */
    ConsensusLayerStatus(final int id) {
        this.id = id;
    }

    @Override
    public int getId() {
        return id;
    }
}
