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

package com.swirlds.platform.roster;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Takes as input a sequence of {@link UpdatedRoster} objects and produces a sequence of {@link RosterDiff} objects.
 */
public class RosterDiffGenerator {

    private static final Logger logger = LogManager.getLogger(RosterDiffGenerator.class);

    private AddressBook previousRoster;
    private long previousEffectiveRound;

    private final Cryptography cryptography;

    /**
     * Constructor.
     *
     * @param platformContext the platform context
     */
    public RosterDiffGenerator(@NonNull final PlatformContext platformContext) {
        cryptography = platformContext.getCryptography();
    }

    /**
     * Given a new roster, generate a diff with respect to the previous roster.
     *
     * @param updatedRoster the new roster, must be already hashed
     * @return the diff, will return null for the very first roster added
     */
    @Nullable
    public RosterDiff generateDiff(@NonNull final UpdatedRoster updatedRoster) {
        final AddressBook roster = updatedRoster.roster();
        final long effectiveRound = updatedRoster.effectiveRound();

        if (roster.getHash() == null) {
            throw new IllegalStateException(
                    "Effective roster for round " + updatedRoster.effectiveRound() + " is unhashed.");
        }

        final RosterDiff diff;

        if (previousRoster == null) {
            // This is the first roster we've seen.
            diff = null;
        } else {
            if (previousEffectiveRound >= effectiveRound) {
                logger.error(
                        EXCEPTION.getMarker(),
                        "Effective rounds should always increase over time. "
                                + "Previous effective round: {}, new effective round: {}",
                        previousEffectiveRound,
                        effectiveRound);
            }

            diff = compareRosters(previousRoster, updatedRoster);
        }

        previousRoster = roster;
        previousEffectiveRound = effectiveRound;

        return diff;
    }

    /**
     * Compare two rosters and generate a diff.
     *
     * @param previousRoster the previous roster
     * @param updatedRoster  describes the roster
     * @return the difference between the new and the previous roster
     */
    @NonNull
    private static RosterDiff compareRosters(
            @NonNull final AddressBook previousRoster, @NonNull final UpdatedRoster updatedRoster) {

        final AddressBook roster = updatedRoster.roster();

        if (roster.getHash().equals(previousRoster.getHash())) {
            // Simple case: the roster is identical to the previous one.
            // Short circuit the diff generation for the sake of efficiency.
            return new RosterDiff(updatedRoster, true, false, false, List.of(), List.of(), List.of());
        }

        final Set<NodeId> previousNodes = previousRoster.getNodeIdSet();
        final Set<NodeId> currentNodes = roster.getNodeIdSet();

        final List<NodeId> removedNodes = new ArrayList<>();
        final List<NodeId> addedNodes = new ArrayList<>();
        final List<NodeId> modifiedNodes = new ArrayList<>();
        boolean consensusWeightChanged = false;
        boolean membershipChanged = false;

        // Find the nodes that have been removed.
        for (final NodeId nodeId : previousNodes) {
            if (!currentNodes.contains(nodeId)) {
                removedNodes.add(nodeId);
                membershipChanged = true;
                consensusWeightChanged = true;
            }
        }

        // Find the nodes that have been added or modified.
        for (final NodeId nodeId : currentNodes) {
            if (previousNodes.contains(nodeId)) {
                final Address previousAddress = previousRoster.getAddress(nodeId);
                final Address currentAddress = roster.getAddress(nodeId);
                if (!previousAddress.equals(currentAddress)) {
                    modifiedNodes.add(nodeId);
                }
                if (previousAddress.getWeight() != currentAddress.getWeight()) {
                    consensusWeightChanged = true;
                }
            } else {
                addedNodes.add(nodeId);
                membershipChanged = true;
                consensusWeightChanged = true;
            }
        }

        return new RosterDiff(
                updatedRoster,
                false,
                consensusWeightChanged,
                membershipChanged,
                addedNodes,
                removedNodes,
                modifiedNodes);
    }
}
