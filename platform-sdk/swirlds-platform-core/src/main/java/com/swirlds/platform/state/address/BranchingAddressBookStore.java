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

import com.swirlds.common.Releasable;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.impl.ComposedMerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.merkle.impl.destroyable.DestroyableMerkleLeaf;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.config.AddressBookConfig;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An {@link AddressBookStore} that supports branching copies (i.e. when a copy is made, both the new and the original
 * remain mutable). This address book is compatible with {@link com.swirlds.common.system.SwirldState SwirldState1}.
 * Although technically compatible with {@link com.swirlds.common.system.SwirldState SwirldState2}, it is less
 * performant.
 */
public class BranchingAddressBookStore extends AbstractAddressBookStore implements ComposedMerkleLeaf {

    private static final long CLASS_ID = 0x367634a9a5cb20d4L;

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    /**
     * This object is used to inherit merkle node boilerplate by composition.
     */
    private final PartialMerkleLeaf merkleNode = new DestroyableMerkleLeaf(this::destroyNode);

    private final Map<Long, AddressBook> addressBookMap = new HashMap<>();
    private AddressBook overridingAddressBook;
    private long earliestRound;
    private long latestRound;

    /**
     * Zero arg constructor required by constructable registry.
     */
    public BranchingAddressBookStore() {
        super(ConfigurationHolder.getConfigData(AddressBookConfig.class));
    }

    /**
     * Copy constructor.
     *
     * @param that the object to copy
     */
    private BranchingAddressBookStore(final BranchingAddressBookStore that) {
        super(that.getAddressBookConfig());
        that.addressBookMap.forEach((final Long round, final AddressBook addressBook) -> {
            this.addressBookMap.put(round, addressBook);
            addressBook.reserve();
        });
        this.overridingAddressBook = that.overridingAddressBook;
        this.overridingAddressBook.reserve();
        this.earliestRound = that.earliestRound;
        this.latestRound = that.latestRound;
    }

    /**
     * Clean up resources that the garbage collector can't clean automatically.
     */
    private synchronized void destroyNode() {
        if (overridingAddressBook != null) {
            overridingAddressBook.release();
        }
        addressBookMap.values().forEach(Releasable::release);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(earliestRound);
        out.writeLong(latestRound);
        out.writeSerializable(overridingAddressBook, false);
        for (long round = earliestRound; round <= latestRound; round++) {
            out.writeSerializable(Objects.requireNonNull(addressBookMap.get(round)), false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        earliestRound = in.readLong();
        latestRound = in.readLong();
        overridingAddressBook = in.readSerializable(false, AddressBook::new);
        for (long round = earliestRound; round <= latestRound; round++) {
            addressBookMap.put(round, Objects.requireNonNull(in.readSerializable(false, AddressBook::new)));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BranchingAddressBookStore copy() {
        return new BranchingAddressBookStore(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PartialMerkleLeaf getMerkleImplementation() {
        return merkleNode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLatestRound() {
        if (getSize() == 0) {
            return UNDEFINED_ROUND;
        }
        return latestRound;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getEarliestRound() {
        if (getSize() == 0) {
            return UNDEFINED_ROUND;
        }
        return earliestRound;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSize() {
        return addressBookMap.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Map<Long, AddressBook> getAddressBookMap() {
        return addressBookMap;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void removeOldest() {
        if (getSize() == 0) {
            throw new IllegalStateException("store is empty, can't remove");
        }
        final AddressBook addressBookToRemove = Objects.requireNonNull(
                addressBookMap.remove(earliestRound), "address book for round not found for round " + earliestRound);
        addressBookToRemove.release();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void appendAddressBook(final AddressBook addressBook) {
        addressBook.reserve();
        addressBookMap.put(addressBook.getRound(), addressBook);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setEarliestRound(final long earliestRound) {
        this.earliestRound = earliestRound;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setLatestRound(final long latestRound) {
        this.latestRound = latestRound;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setOverridingAddressBook(final AddressBook overridingAddressBook) {
        this.overridingAddressBook = overridingAddressBook;
        overridingAddressBook.reserve();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected AddressBook getOverridingAddressBook() {
        return overridingAddressBook;
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
