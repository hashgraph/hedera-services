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

import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.merkle.impl.ComposedMerkleInternal;
import com.swirlds.common.merkle.impl.PartialBinaryMerkleInternal;
import com.swirlds.common.merkle.impl.PartialMerkleInternal;
import com.swirlds.common.merkle.impl.destroyable.DestroyableBinaryMerkleInternal;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.fchashmap.FCHashMap;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.platform.config.AddressBookConfig;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * An {@link AddressBookStore} that is included in the state. This address book store is sequential. That is, once a
 * copy is made, the old copy becomes immutable.
 */
public class SequentialAddressBookStore extends AbstractAddressBookStore implements ComposedMerkleInternal {

    private static final long CLASS_ID = 0x7888bfe140af1d31L;

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private static class ChildIndices {
        public static final int ADDRESS_BOOKS = 0;
        public static final int STORE_INFO = 1;
    }

    private final FCHashMap<Long, AddressBook> map;

    private final PartialBinaryMerkleInternal merkleNode = new DestroyableBinaryMerkleInternal(this::destroyNode);

    public SequentialAddressBookStore() {
        super(ConfigurationHolder.getConfigData(AddressBookConfig.class));
        map = new FCHashMap<>();
        setAddressBooks(new FCQueue<>());
        setStoreInfo(new SequentialAddressBookStoreInfo());
    }

    /**
     * Copy constructor.
     *
     * @param that the store to copy
     */
    private SequentialAddressBookStore(final SequentialAddressBookStore that) {
        super(that.getAddressBookConfig());
        this.setChild(ChildIndices.ADDRESS_BOOKS, that.getAddressBooks().copy());
        this.setChild(ChildIndices.STORE_INFO, that.getStoreInfo().copy());

        // FUTURE WORK: FCQueue doesn't currently reserve/release things in the queue, but it really should!
        for (final AddressBook addressBook : getAddressBooks()) {
            addressBook.reserve();
        }

        this.map = that.map.copy();
    }

    /**
     * Get a queue of recent address books.
     *
     * @return a queue of recent address books
     */
    private FCQueue<AddressBook> getAddressBooks() {
        return getChild(ChildIndices.ADDRESS_BOOKS);
    }

    /**
     * Set the address book queue.
     *
     * @param addressBooks a queue of address books
     */
    private void setAddressBooks(final FCQueue<AddressBook> addressBooks) {
        setChild(ChildIndices.ADDRESS_BOOKS, addressBooks);
    }

    /**
     * Get the object that contains information about the address book.
     *
     * @return an object containing information about the address book
     */
    private SequentialAddressBookStoreInfo getStoreInfo() {
        return getChild(ChildIndices.STORE_INFO);
    }

    /**
     * Get the object that contains information about the address book.
     *
     * @param storeInfo an object containing information about the address book
     */
    private void setStoreInfo(final SequentialAddressBookStoreInfo storeInfo) {
        setChild(ChildIndices.STORE_INFO, storeInfo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SequentialAddressBookStore copy() {
        throwIfImmutable();
        final SequentialAddressBookStore copy = new SequentialAddressBookStore(this);
        merkleNode.setImmutable(true);
        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void rebuild() {
        final Iterator<AddressBook> iterator = getAddressBooks().iterator();
        for (long round = getEarliestRound(); round <= getLatestRound(); round++) {
            final AddressBook next = Objects.requireNonNull(iterator.next());
            if (round != next.getRound()) {
                throw new IllegalStateException("address book has invalid round");
            }
            map.put(round, next);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PartialMerkleInternal getMerkleImplementation() {
        return merkleNode;
    }

    @Override
    protected Map<Long, AddressBook> getAddressBookMap() {
        return map;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void removeOldest() {
        if (getSize() == 0) {
            throw new IllegalStateException("store is empty, can't remove");
        }

        Objects.requireNonNull(getAddressBooks().remove(), "Invalid address book removed from queue");

        final AddressBook addressBookToRemove = Objects.requireNonNull(
                map.remove(getEarliestRound()), "Address book for round " + getEarliestRound() + "not found");

        addressBookToRemove.release();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void appendAddressBook(final AddressBook addressBook) {
        addressBook.reserve();
        getAddressBooks().add(addressBook);
        map.put(addressBook.getRound(), addressBook);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setEarliestRound(final long earliestRound) {
        getStoreInfo().setEarliestRound(earliestRound);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setLatestRound(final long latestRound) {
        getStoreInfo().setLatestRound(latestRound);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setOverridingAddressBook(final AddressBook overridingAddressBook) {
        getStoreInfo().setOverridingAddressBook(overridingAddressBook);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected AddressBook getOverridingAddressBook() {
        return getStoreInfo().getOverridingAddressBook();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLatestRound() {
        if (getSize() == 0) {
            return UNDEFINED_ROUND;
        }
        return getStoreInfo().getLatestRound();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getEarliestRound() {
        if (getSize() == 0) {
            return UNDEFINED_ROUND;
        }
        return getStoreInfo().getEarliestRound();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSize() {
        return getAddressBooks().size();
    }

    /**
     * Clean up resources that the garbage collector can't clean automatically.
     */
    private synchronized void destroyNode() {
        if (map != null && !map.isDestroyed()) {
            map.release();
        }
        // FUTURE WORK: FCQueue doesn't currently reserve/release things in the queue, but it really should!
        for (final AddressBook addressBook : getAddressBooks()) {
            addressBook.release();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }
}
