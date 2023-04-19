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

import static com.swirlds.logging.LogMarker.EXCEPTION;

import edu.umd.cs.findbugs.annotations.NonNull;
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

        if (previousAddressBook.getNextNodeId() > addressBook.getNextNodeId()) {
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
     * No address that is removed may be re-added to the address book. If address N is skipped and address N+1 is later
     * added, then address N can never be added (as this is difficult to distinguish from N being added and then
     * removed).
     *
     * @param previousAddressBook the previous address book
     * @param addressBook         the address book to validate
     * @return if the address book passes this validation
     */
    public static boolean noAddressReinsertion(final AddressBook previousAddressBook, final AddressBook addressBook) {

        final long previousNextId = previousAddressBook.getNextNodeId();
        for (final Address address : addressBook) {
            final long nodeId = address.getId();
            if (nodeId < previousNextId && !previousAddressBook.contains(nodeId)) {
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
                    final long nodeId1 = addressBook1.getId(i);
                    final long nodeId2 = addressBook2.getId(i);
                    final Address address1 = addressBook1.getAddress(nodeId1);
                    final Address address2 = addressBook2.getAddress(nodeId2);
                    if (address1 == null || address2 == null) {
                        logger.error(EXCEPTION.getMarker(), "Address at index {} is null when accessed in order.", i);
                        throw new IllegalStateException("Address at index " + i + " is null.");
                    }
                    final boolean equal = address1.equalsWithoutWeightAndOwnHost(address2);
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
