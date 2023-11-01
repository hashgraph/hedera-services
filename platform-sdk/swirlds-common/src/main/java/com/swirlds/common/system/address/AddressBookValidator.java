/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.system.address;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.common.system.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class provides methods for validating new address books. These methods do not throw exceptions if validation
 * fails, but they are intentionally very noisy in the logs when they fail.
 */
public final class AddressBookValidator {

    private static final Logger logger = LogManager.getLogger(AddressBookValidator.class);

    private AddressBookValidator() {}

    // FUTURE WORK: this class is only partially completed. Additional validation will be added before changing address
    // books at runtime is fully supported.

    /**
     * Make sure the address book has at least some weight.
     *
     * @param addressBook the address book to validate
     * @return if the address book passes this validation
     */
    public static boolean hasNonZeroWeight(final AddressBook addressBook) {
        if (addressBook.getTotalWeight() <= 0) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "address book for round {} has {} total weight",
                    addressBook.getRound(),
                    addressBook.getTotalWeight());
            return false;
        }
        return true;
    }

    /**
     * Make sure the address book is not empty.
     *
     * @param addressBook the address book to validate
     * @return if the address book passes this validation
     */
    public static boolean isNonEmpty(final AddressBook addressBook) {
        if (addressBook.getSize() == 0) {
            logger.error(EXCEPTION.getMarker(), "address book for round {} is empty", addressBook.getRound());
            return false;
        }
        return true;
    }

    /**
     * Sanity check, make sure that the next address book has a next ID greater or equal to the previous one.
     *
     * @param previousAddressBook the previous address book
     * @param addressBook         the address book to validate
     * @return if the address book passes this validation
     */
    public static boolean validNextId(final AddressBook previousAddressBook, final AddressBook addressBook) {

        if (previousAddressBook.getNextNodeId().compareTo(addressBook.getNextNodeId()) > 0) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Invalid next node ID. Previous address book has a next node ID of {}, "
                            + "new address book has a next node ID of {}",
                    previousAddressBook.getNextNodeId(),
                    addressBook.getNextNodeId());
            return false;
        }

        return true;
    }

    /**
     * Validates the following properties:
     * <ul>
     * <li>newAddressBook.getNextNodeId() is greater than or equal to oldAddressBook.getNextNodeId() </li>
     * <li>for each nodeId in newAddressBook that is not in oldAddressBook:
     * <ul>
     *     <li>the nodeId is greater than or equal to oldAddressBook.getNextNodeId()</li>
     *     <li>the nodeId is less than newAddressBook.getNextNodeId()</li>
     * </ul>
     * </li>
     * </ul>
     *
     * @param oldAddressBook the old address book
     * @param newAddressBook the new address book
     * @throws IllegalStateException if the nextNodeId in the new address book is less than or equal to the nextNodeId
     *                               in the old address book, or if there are any new nodes in the new address book that
     *                               are less than the old nextNodeId or greater than or equal to the new nextNodeId.
     */
    public static void validateNewAddressBook(
            @NonNull final AddressBook oldAddressBook, @NonNull final AddressBook newAddressBook) {

        final NodeId oldNextNodeId = oldAddressBook.getNextNodeId();
        final NodeId newNextNodeId = newAddressBook.getNextNodeId();

        if (newNextNodeId.compareTo(oldNextNodeId) < 0) {
            throw new IllegalStateException("The new address book's nextNodeId " + newNextNodeId
                    + " must be greater than or equal to the previous address book's nextNodeId "
                    + oldNextNodeId);
        }

        final int oldSize = oldAddressBook.getSize();
        final int newSize = newAddressBook.getSize();

        // Verify that the old next node id is greater than the highest node id in the old address book.
        final NodeId oldLastNodeId = (oldSize == 0 ? null : oldAddressBook.getNodeId(oldSize - 1));
        if (oldLastNodeId != null && oldLastNodeId.compareTo(oldNextNodeId) > 0) {
            throw new IllegalStateException(
                    "The nextNodeId of the previous address book must be greater than the highest address's node id.");
        }

        // Determine the new node ids that are in the new address book and not in the old address book.
        final List<NodeId> newNodes = new ArrayList<>();
        for (int i = 0; i < newSize; i++) {
            final NodeId newNodeId = newAddressBook.getNodeId(i);
            if (!oldAddressBook.contains(newNodeId)) {
                newNodes.add(newNodeId);
            }
        }

        // verify that all new nodes are greater than or equal to oldNextNodeId and less than newNextNodeId.
        for (final NodeId nodeId : newNodes) {
            if (nodeId.compareTo(oldNextNodeId) < 0) {
                throw new IllegalStateException("The new node " + nodeId
                        + " is less than the previous address book's nextNodeId " + oldNextNodeId);
            }
            if (nodeId.compareTo(newNextNodeId) >= 0) {
                throw new IllegalStateException("The new node " + nodeId
                        + " is greater than or equal to the new address book's nextNodeId " + newNextNodeId);
            }
        }
    }

    /**
     * No address that is removed may be re-added to the address book. If address N is skipped and address N+1 is later
     * added, then address N can never be added (as this is difficult to distinguish from N being added and then
     * removed).
     *
     * @param previousAddressBook the previous address book
     * @param addressBook         the address book to validate
     * @return if the address book passes this validation
     */
    public static boolean noAddressReinsertion(final AddressBook previousAddressBook, final AddressBook addressBook) {

        final NodeId previousNextId = previousAddressBook.getNextNodeId();
        for (final Address address : addressBook) {
            final NodeId nodeId = address.getNodeId();
            if (nodeId.compareTo(previousNextId) < 0 && !previousAddressBook.contains(nodeId)) {
                logger.error(
                        EXCEPTION.getMarker(),
                        "Once an address is removed or a node ID is skipped, "
                                + "an address with that some node ID may never be added again. Invalid node ID = {}",
                        nodeId);
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a genesis address book is valid. In the case of an invalid address book, this method will write an error
     * message to the log.
     *
     * @param candidateAddressBook a candidate address book that is being tested for validity
     * @return true if the address book is valid
     */
    public static boolean isGenesisAddressBookValid(final AddressBook candidateAddressBook) {

        return hasNonZeroWeight(candidateAddressBook) && isNonEmpty(candidateAddressBook);
    }

    /**
     * Check if a new address book transition is valid. In the case of an invalid address book, this method will write
     * an error message to the log.
     *
     * @param previousAddressBook  the previous address book, is assumed to be valid
     * @param candidateAddressBook the new address book that follows the current address book
     * @return true if the transition is valid
     */
    public static boolean isNextAddressBookValid(
            final AddressBook previousAddressBook, final AddressBook candidateAddressBook) {

        return hasNonZeroWeight(candidateAddressBook)
                && isNonEmpty(candidateAddressBook)
                && validNextId(previousAddressBook, candidateAddressBook)
                && noAddressReinsertion(previousAddressBook, candidateAddressBook);
    }

    /**
     * Checks that the addresses between the two address books are identical except for weight value.
     *
     * @param addressBook1 An address book to compare for equality.
     * @param addressBook2 An address book to compare for equality.
     * @return true of the two address books contain the same addresses except for weight values, false otherwise.
     */
    public static boolean sameExceptForWeight(
            @NonNull final AddressBook addressBook1, @NonNull final AddressBook addressBook2) {
        Objects.requireNonNull(addressBook1, "addressBook1 must not be null");
        Objects.requireNonNull(addressBook2, "addressBook2 must not be null");
        final int addressBookSize = addressBook1.getSize();
        if (addressBookSize != addressBook2.getSize()) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Address books have different sizes. Address book 1 has size {}, address book 2 has size {}.",
                    addressBookSize,
                    addressBook2.getSize());
            return false;
        }
        return IntStream.range(0, addressBookSize)
                .mapToObj(i -> {
                    final NodeId nodeId1 = addressBook1.getNodeId(i);
                    final NodeId nodeId2 = addressBook2.getNodeId(i);
                    final Address address1 = addressBook1.getAddress(nodeId1);
                    final Address address2 = addressBook2.getAddress(nodeId2);
                    if (address1 == null || address2 == null) {
                        logger.error(EXCEPTION.getMarker(), "Address at index {} is null when accessed in order.", i);
                        throw new IllegalStateException("Address at index " + i + " is null.");
                    }
                    final boolean equal = address1.equalsWithoutWeight(address2);
                    if (!equal) {
                        logger.error(
                                EXCEPTION.getMarker(),
                                "Address at position {} is not the same between the two address books.",
                                i);
                    }
                    return equal;
                })
                .reduce((left, right) -> left && right)
                .orElse(false);
    }
}
