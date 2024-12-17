/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.platform.test.fixtures.addressbook;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Random;

/**
 * Utilities for manipulating rosters in tests.
 */
public class RosterTestUtils {

    private RosterTestUtils() {}

    /**
     * returns a new roster with the same RosterEntries, minus the RosterEntry matching the given NodeId.
     *
     * @param roster the roster to remove the entry from
     * @param nodeId the nodeId of the entry to remove
     * @return a new roster with the same RosterEntries, minus the RosterEntry matching the given NodeId
     */
    public static Roster dropRosterEntryFromRoster(@NonNull final Roster roster, final long nodeId) {
        final Roster.Builder builder = Roster.newBuilder();

        final ArrayList<RosterEntry> rosterEntries = new ArrayList<>();
        roster.rosterEntries().stream()
                .filter(entry -> entry.nodeId() != nodeId)
                .forEach(rosterEntries::add);

        builder.rosterEntries(rosterEntries);
        return builder.build();
    }

    /**
     * returns a new roster with the same RosterEntries, plus a new RosterEntry with the given NodeId.
     *
     * @param roster the roster to add the entry to
     * @param nodeId the nodeId of the entry to add
     * @param random the random number generator to use
     * @return a new roster with the same RosterEntries, plus a new RosterEntry with the given NodeId
     */
    public static Roster addRandomRosterEntryToRoster(
            @NonNull final Roster roster, final long nodeId, @NonNull final Random random) {
        final Roster.Builder builder = Roster.newBuilder();

        final ArrayList<RosterEntry> rosterEntries = new ArrayList<>(roster.rosterEntries());
        final RosterEntry entry =
                RandomRosterEntryBuilder.create(random).withNodeId(nodeId).build();
        rosterEntries.add(entry);

        builder.rosterEntries(rosterEntries);
        return builder.build();
    }

    /**
     * Replace a node in the specified roster with the provided entry.
     *
     * @param roster the roster to modify
     * @param node the replacement node
     * @return true if the roster contained a node with the same node ID as the specified replacement node and it was
     * replaced, else false
     */
    public static boolean replaceNode(@NonNull final Roster roster, @NonNull final RosterEntry node) {
        Objects.requireNonNull(roster, "roster");
        Objects.requireNonNull(node, "node");

        for (int i = 0; i < roster.rosterEntries().size(); ++i) {
            final RosterEntry thisNode = roster.rosterEntries().get(i);
            if (thisNode.nodeId() == node.nodeId()) {
                roster.rosterEntries().set(i, node);
                return true;
            }
        }

        return false;
    }
}
