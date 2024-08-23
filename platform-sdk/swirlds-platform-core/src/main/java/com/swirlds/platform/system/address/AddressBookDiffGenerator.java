/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.system.address;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Takes as input a sequence of {@link RoundAddressBookRecord} objects and produces a sequence of {@link AddressBookDiff} objects.
 */
public class AddressBookDiffGenerator {

    private static final Logger logger = LogManager.getLogger(AddressBookDiffGenerator.class);

    private AddressBook previousAddressBook;
    private long previousEffectiveRound;

    private final Cryptography cryptography;

    /**
     * Constructor.
     *
     * @param platformContext the platform context
     */
    public AddressBookDiffGenerator(@NonNull final PlatformContext platformContext) {
        cryptography = platformContext.getCryptography();
    }

    /**
     * Given a new addressBook, generate a diff with respect to the previous addressBook.
     *
     * @param roundAddressBookRecord the new addressBook, must be already hashed
     * @return the diff, will return null for the very first addressBook added
     */
    @Nullable
    public AddressBookDiff generateDiff(@NonNull final RoundAddressBookRecord roundAddressBookRecord) {
        final AddressBook addressBook = roundAddressBookRecord.addressBook();
        final long effectiveRound = roundAddressBookRecord.effectiveRound();

        if (addressBook.getHash() == null) {
            throw new IllegalStateException(
                    "Effective addressBook for round " + roundAddressBookRecord.effectiveRound() + " is unhashed.");
        }

        final AddressBookDiff diff;

        if (previousAddressBook == null) {
            // This is the first addressBook we've seen.
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

            diff = compareAddressBooks(previousAddressBook, roundAddressBookRecord);
        }

        previousAddressBook = addressBook;
        previousEffectiveRound = effectiveRound;

        return diff;
    }

    /**
     * Compare two addressBooks and generate a diff.
     *
     * @param previousAddressBook the previous addressBook
     * @param roundAddressBookRecord  describes the addressBook
     * @return the difference between the new and the previous addressBook
     */
    @NonNull
    private static AddressBookDiff compareAddressBooks(
            @NonNull final AddressBook previousAddressBook,
            @NonNull final RoundAddressBookRecord roundAddressBookRecord) {

        final AddressBook addressBook = roundAddressBookRecord.addressBook();

        if (addressBook.getHash().equals(previousAddressBook.getHash())) {
            // Simple case: the addressBook is identical to the previous one.
            // Short circuit the diff generation for the sake of efficiency.
            return new AddressBookDiff(roundAddressBookRecord, true, false, false, List.of(), List.of(), List.of());
        }

        final Set<NodeId> previousNodes = previousAddressBook.getNodeIdSet();
        final Set<NodeId> currentNodes = addressBook.getNodeIdSet();

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
                final Address previousAddress = previousAddressBook.getAddress(nodeId);
                final Address currentAddress = addressBook.getAddress(nodeId);
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

        return new AddressBookDiff(
                roundAddressBookRecord,
                false,
                consensusWeightChanged,
                membershipChanged,
                addedNodes,
                removedNodes,
                modifiedNodes);
    }
}
