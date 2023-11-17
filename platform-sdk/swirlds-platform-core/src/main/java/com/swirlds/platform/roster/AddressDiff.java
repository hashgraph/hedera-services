package com.swirlds.platform.roster;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.Address;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Describes the change in an address. Although it is possible to derive this information by comparing the
 * {@link Address} objects directly, this data is distilled and provided in this format for convenience.
 *
 * @param nodeId             the node ID of the address with the change
 * @param newIpAddress       whether the IP address/port has changed
 * @param newKeys            whether the keys have changed
 * @param newConsensusWeight whether the consensus weight has changed
 */
public record AddressDiff(
        @NonNull NodeId nodeId,
        boolean newIpAddress,
        boolean newKeys,
        boolean newConsensusWeight) {
}
