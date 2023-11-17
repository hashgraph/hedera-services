package com.swirlds.platform.roster;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Describes the difference between two rosters. Although it is possible to derive this information by comparing the
 * {@link AddressBook} objects directly, this data is distilled and provided in this format for convenience.
 *
 * @param consensusWeightChanged whether the consensus weight changed
 * @param membershipChanged      whether the membership changed
 * @param addedNodes             the nodes that were added
 * @param removedNodes           the nodes that were removed
 * @param modifiedNodes          the nodes that were modified
 */
public record RosterDiff(
        boolean rosterIsIdentical,
        boolean consensusWeightChanged,
        boolean membershipChanged,
        @NonNull List<NodeId> addedNodes,
        @NonNull List<NodeId> removedNodes,
        @NonNull List<AddressDiff> modifiedNodes) {
}
