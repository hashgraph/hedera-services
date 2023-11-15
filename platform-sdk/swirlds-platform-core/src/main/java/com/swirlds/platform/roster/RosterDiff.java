package com.swirlds.platform.roster;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Describes the difference between two rosters. Although it is possible to derive this information by comparing the
 * {@link AddressBook} objects directly, this data is distilled and provided in this format for convenience.
 *
 * @param previousRoster         the previous roster
 * @param newRoster              the new roster
 * @param addedNodes             the nodes that were added
 * @param removedNodes           the nodes that were removed
 * @param modifiedNodes          the nodes that were modified
 * @param rostersAreIdentical    whether the rosters are identical
 * @param consensusWeightChanged whether the consensus weight changed
 * @param membershipChanged      whether the membership changed
 */
public record RosterDiff(
        @NonNull AddressBook previousRoster,
        @NonNull AddressBook newRoster,
        @NonNull List<NodeId> addedNodes,
        @NonNull List<NodeId> removedNodes,
        @NonNull List<AddressDiff> modifiedNodes,
        boolean rostersAreIdentical,
        boolean consensusWeightChanged,
        boolean membershipChanged) {
}
