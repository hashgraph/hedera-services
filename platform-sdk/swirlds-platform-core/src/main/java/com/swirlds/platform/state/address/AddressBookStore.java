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

package com.swirlds.platform.state.address;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.sequence.Shiftable;
import com.swirlds.common.system.address.AddressBook;

/**
 * An in-state collection of address books from recent rounds.
 */
public interface AddressBookStore extends MerkleNode, Shiftable {

    int UNDEFINED_ROUND = -1;

    /**
     * Get the address book associated with a round.
     *
     * @param round the target round
     * @return the address book for the target round
     * @throws java.util.NoSuchElementException if the requested round is not available
     */
    AddressBook get(final long round);

    /**
     * Get the round number of the latest address book.
     *
     * @return the latest round with an address book, return value is undefined if the store is empty
     */
    long getLatestRound();

    /**
     * Get the most recent address book currently in this collection.
     *
     * @return the latest address book, return value is {@link #UNDEFINED_ROUND} if the store is empty
     */
    AddressBook getLatest();

    /**
     * Get the round number of the oldest address book currently in this collection.
     *
     * @return the oldest round with an address book, return value is {@link #UNDEFINED_ROUND} if the store is empty
     */
    long getEarliestRound();

    /**
     * Get the oldest address book in this collection.
     *
     * @return the oldest address book
     */
    AddressBook getEarliest();

    /**
     * Check if this address book store has a particular round.
     *
     * @param round the round in question
     * @return true if the round is present in the store, otherwise false
     */
    default boolean contains(final long round) {
        return round >= getEarliestRound() && round <= getLatestRound();
    }

    /**
     * Get the number of address books in the store.
     */
    int getSize();

    /**
     * <p>
     * Add the next address book to the store. Once added to a store, an address book becomes immutable.
     * </p>
     *
     * <p>
     * If the address book is invalid and this is the initial address book then this method will throw an exception. If
     * the address book is invalid and this is not the initial address book then this method will log an error and copy
     * forward the previous address book.
     * </p>
     *
     * <p>
     * There is currently a known issue with consensus where it may under rare circumstances skip over a round (this
     * only happens if there are no events that reach consensus within a round). To compensate for that issue, the
     * address book store will carry forward copies of the address book when rounds are skipped, meaning that there will
     * never be a gap in an address book store. When that quirk in consensus is eventually fixed then this automatic gap
     * filling behavior will be removed from address book stores.
     * </p>
     *
     * @param addressBook the address book
     */
    void add(final AddressBook addressBook);

    /**
     * Evict address books from the store for old rounds.
     *
     * @param earliestRound the earliest round that should remain in the store. All earlier rounds will be removed.
     */
    @Override
    void shiftWindow(final long earliestRound);

    /**
     * Calling this method causes the overriding address book to change to the latest available address book. This
     * method, if called at all, should only be called when the node boots up.
     */
    void updateOverridingAddressBook();

    /**
     * {@inheritDoc}
     */
    @Override
    AddressBookStore copy();
}
