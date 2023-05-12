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

import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.futures.SequentialFutures;
import com.swirlds.common.threading.futures.StandardFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An address book manager that can be modified.
 */
public class AddressBookManagerImpl implements MutableAddressBookManager {

    private static final Logger logger = LogManager.getLogger(AddressBookManagerImpl.class);

    private final AtomicReference<AddressBook> mostRecentAddressBook = new AtomicReference<>();
    private final SequentialFutures<AddressBook> futures;

    private final int numberOfRoundsToStore;

    /**
     * Create a new address book manager.
     *
     * @param initialStore
     * 		the initial address book
     * @param numberOfRoundsToStore
     * 		the total number of rounds to keep in memory
     */
    public AddressBookManagerImpl(final AddressBookStore initialStore, final int numberOfRoundsToStore) {

        // FUTURE WORK: fix skipped rounds so that we don't need to tolerate gaps

        if (initialStore.getSize() < numberOfRoundsToStore) {
            throw new IllegalArgumentException("The initial store has " + initialStore.getSize() + " rounds, but "
                    + numberOfRoundsToStore + " rounds are needed to initialize the address book manager");
        }

        this.numberOfRoundsToStore = numberOfRoundsToStore;
        futures = new SequentialFutures<>(
                initialStore.getLatestRound() + 1,
                numberOfRoundsToStore,
                round -> initialStore.getEarliest(),
                (final long index, final StandardFuture<AddressBook> future, final AddressBook previousValue) -> {
                    logger.warn(
                            STARTUP.getMarker(),
                            "Address book for round {} skipped. Expected on round 1. "
                                    + "Probably a bug if it happens for a later round.",
                            index);
                    future.complete(previousValue.copy().setRound(index));
                });
        mostRecentAddressBook.set(initialStore.getLatest());
    }

    /**
     * Set the most recent address book store from a recently copied state.
     *
     * @param mostRecentAddressBook
     * 		the most recent address book
     */
    @Override
    public void setMostRecentAddressBook(final AddressBook mostRecentAddressBook) {
        this.mostRecentAddressBook.set(mostRecentAddressBook);
        futures.complete(mostRecentAddressBook.getRound(), mostRecentAddressBook);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fastForwardToAddressBook(final AddressBookStore newStore) {
        if (newStore.getSize() < numberOfRoundsToStore) {
            throw new IllegalArgumentException("The new store has " + newStore.getSize() + " rounds, but "
                    + numberOfRoundsToStore + " rounds are needed to initialize the address book manager");
        }
        futures.fastForwardIndex(newStore.getLatestRound() + 1, newStore::get);
        mostRecentAddressBook.set(newStore.getLatest());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AddressBook get(final long round) throws InterruptedException {
        try {
            return futures.getValue(round);
        } catch (final ExecutionException e) {
            // This should never throw an execution exception, but we have to catch it to satisfy the compiler.
            throw new RuntimeException("Unable to get address book for round " + round + " due to exception", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AddressBook getLatest() {
        return mostRecentAddressBook.get();
    }
}
