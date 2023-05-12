/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.system.address.AddressBook;
import java.io.IOException;

/**
 * Contains info required by a {@link SequentialAddressBookStore}.
 */
public class SequentialAddressBookStoreInfo extends PartialMerkleLeaf implements MerkleLeaf {

    private static final long CLASS_ID = 0xf1e60b44b32c0882L;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private long earliestRound;
    private long latestRound;

    private AddressBook overridingAddressBook;

    public SequentialAddressBookStoreInfo() {}

    /**
     * Copy constructor.
     *
     * @param that
     * 		the node to copy
     */
    private SequentialAddressBookStoreInfo(final SequentialAddressBookStoreInfo that) {
        this.earliestRound = that.earliestRound;
        this.latestRound = that.latestRound;
        if (that.overridingAddressBook != null) {
            this.overridingAddressBook = that.overridingAddressBook;
            this.overridingAddressBook.reserve();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SequentialAddressBookStoreInfo copy() {
        return new SequentialAddressBookStoreInfo(this);
    }

    /**
     * Get the earliest round in the address book store.
     *
     * @return the earliest round in the store
     */
    public long getEarliestRound() {
        return earliestRound;
    }

    /**
     * Set the earliest round in the address book store.
     *
     * @param earliestRound
     * 		the earliest round in the store
     */
    public void setEarliestRound(final long earliestRound) {
        this.earliestRound = earliestRound;
    }

    /**
     * Get the latest round in the address book store.
     *
     * @return the latest round in the address book store
     */
    public long getLatestRound() {
        return latestRound;
    }

    /**
     * Set the latest round in the address book store.
     *
     * @param latestRound
     * 		the latest round in the address book store
     */
    public void setLatestRound(final long latestRound) {
        this.latestRound = latestRound;
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
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(earliestRound);
        out.writeLong(latestRound);
        out.writeSerializable(overridingAddressBook, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        earliestRound = in.readLong();
        latestRound = in.readLong();
        overridingAddressBook = in.readSerializable();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }

    /**
     * Get the address book that should override the other address books in the store.
     *
     * @return the overriding address book
     */
    public AddressBook getOverridingAddressBook() {
        return overridingAddressBook;
    }

    /**
     * Set the address book that should override the other address books in the store
     *
     * @param overridingAddressBook
     * 		the overriding address book
     */
    public void setOverridingAddressBook(final AddressBook overridingAddressBook) {
        this.overridingAddressBook = overridingAddressBook;
        overridingAddressBook.reserve();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void destroyNode() {
        if (overridingAddressBook != null) {
            overridingAddressBook.release();
        }
    }
}
