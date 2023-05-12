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

import static com.swirlds.common.system.address.AddressBookValidator.isGenesisAddressBookValid;
import static com.swirlds.common.system.address.AddressBookValidator.isNextAddressBookValid;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.config.AddressBookConfig;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An {@link AddressBookStore} that is included in the state.
 */
public abstract class AbstractAddressBookStore implements AddressBookStore {

    private static final Logger logger = LogManager.getLogger(AbstractAddressBookStore.class);

    private final AddressBookConfig addressBookConfig;

    protected AbstractAddressBookStore(final AddressBookConfig addressBookConfig) {
        this.addressBookConfig = CommonUtils.throwArgNull(addressBookConfig, "addressBookConfig");
    }

    /**
     * Get a map of rounds to address books.
     *
     * @return a map of rounds to address books
     */
    protected abstract Map<Long /* round */, AddressBook> getAddressBookMap();

    /**
     * Remove the address book for the oldest round (i.e. the address book for the round returned by
     * {@link #getEarliestRound()}).
     */
    protected abstract void removeOldest();

    /**
     * Add an address book for the new newest round. This method is not required to do any special gap handling,
     * and is not expected to update any data such as the value of the latest round.
     *
     * @param addressBook
     * 		the new address book
     */
    protected abstract void appendAddressBook(final AddressBook addressBook);

    /**
     * Set the earliest round in the address book store.
     *
     * @param earliestRound
     * 		the earliest round in the store
     */
    protected abstract void setEarliestRound(final long earliestRound);

    /**
     * Set the latest round in the address book store.
     *
     * @param latestRound
     * 		the latest round in the address book store
     */
    protected abstract void setLatestRound(final long latestRound);

    /**
     * Set the address book that should override the other address books in the store
     *
     * @param overridingAddressBook
     * 		the overriding address book
     */
    protected abstract void setOverridingAddressBook(final AddressBook overridingAddressBook);

    /**
     * Get the address book that should override the other address books in the store.
     *
     * @return the overriding address book
     */
    protected abstract AddressBook getOverridingAddressBook();

    /**
     * {@inheritDoc}
     */
    @Override
    public void add(final AddressBook addressBook) {
        throwIfImmutable();
        Objects.requireNonNull(addressBook, "null address books are not supported");

        final long round = addressBook.getRound();

        if (getSize() == 0) {
            // This is the first address book being added
            if (!isGenesisAddressBookValid(addressBook)) {
                throw new IllegalStateException("invalid initial address book");
            }
            setEarliestRound(round);
            setOverridingAddressBook(addressBook);
            appendAddressBook(addressBook.seal());
            setLatestRound(round);
        } else {
            // The first round has already been added
            if (round <= getLatestRound()) {
                throw new IllegalStateException("Expected address book for round " + (getLatestRound() + 1)
                        + ", got address book for round " + round);
            }

            if (round != getLatestRound() + 1) {
                logger.warn(
                        STARTUP.getMarker(),
                        "gap in address books detected, "
                                + "address book store was expecting an address book from round {}  but got the address "
                                + "book "
                                + "from round {} instead. This is is not a big deal when it happens at genesis, "
                                + "but more likely to be bug if it happens later than genesis.",
                        (getLatestRound() + 1),
                        round);

                final AddressBook gapFiller = getAddressBookMap().get(getLatestRound());
                for (long roundWithGap = getLatestRound() + 1; roundWithGap < round; roundWithGap++) {
                    appendAddressBook(gapFiller.copy().setRound(roundWithGap).seal());
                    setLatestRound(roundWithGap);
                }
            }

            if (!isNextAddressBookValid(getLatest(), addressBook)) {
                logger.error(EXCEPTION.getMarker(), "address book for round {} is invalid", addressBook.getRound());

                // carry forward the previous address book
                appendAddressBook(getAddressBookMap()
                        .get(getLatestRound())
                        .copy()
                        .setRound(addressBook.getRound())
                        .seal());
            } else {
                // the address book is valid
                appendAddressBook(addressBook.seal());
            }

            setLatestRound(round);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shiftWindow(final long earliestRound) {
        throwIfImmutable();
        for (long round = getEarliestRound(); round < earliestRound; round++) {
            removeOldest();
            setEarliestRound(round + 1);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AddressBook get(final long round) {
        final long earliestRound = getEarliestRound();
        final long latestRound = getLatestRound();

        if (getSize() == 0 || round < getEarliestRound() || round > getLatestRound()) {
            throw new NoSuchElementException("Address book store contains data between rounds " + earliestRound
                    + " and " + latestRound + " (inclusive), cannot get address book for round " + round);
        }

        final AddressBook addressBook = addressBookConfig.updateAddressBookOnlyAtUpgrade()
                ? getOverridingAddressBook()
                : getAddressBookMap().get(round);

        return Objects.requireNonNull(addressBook, "the returned address book should never be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AddressBook getLatest() {
        return get(getLatestRound());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AddressBook getEarliest() {
        return get(getEarliestRound());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateOverridingAddressBook() {
        setOverridingAddressBook(getAddressBookMap().get(getLatestRound()));
    }

    protected AddressBookConfig getAddressBookConfig() {
        return addressBookConfig;
    }
}
