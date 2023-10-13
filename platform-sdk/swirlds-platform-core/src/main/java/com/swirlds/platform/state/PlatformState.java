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

package com.swirlds.platform.state;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import com.swirlds.common.system.address.AddressBook;

/**
 * This subtree contains state data which is managed and used exclusively by the platform.
 */
public class PlatformState extends PartialNaryMerkleInternal implements MerkleInternal {

    public static final long CLASS_ID = 0x483ae5404ad0d0bfL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
        public static final int ADDED_PREVIOUS_ADDRESS_BOOK = 2;
    }

    private static final class ChildIndices {
        public static final int PLATFORM_DATA = 0;
        public static final int ADDRESS_BOOK = 1;
        public static final int PREVIOUS_ADDRESS_BOOK = 2;
    }

    public PlatformState() {}

    /**
     * Copy constructor.
     *
     * @param that
     * 		the node to copy
     */
    private PlatformState(final PlatformState that) {
        super(that);
        if (that.getPlatformData() != null) {
            this.setPlatformData(that.getPlatformData().copy());
        }
        if (that.getAddressBook() != null) {
            this.setAddressBook(that.getAddressBook().copy());
        }
        if (that.getPreviousAddressBook() != null) {
            this.setPreviousAddressBook(that.getPreviousAddressBook().copy());
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
        return ClassVersion.ADDED_PREVIOUS_ADDRESS_BOOK;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PlatformState copy() {
        return new PlatformState(this);
    }

    /**
     * Get the address book.
     */
    public AddressBook getAddressBook() {
        return getChild(ChildIndices.ADDRESS_BOOK);
    }

    /**
     * Set the address book.
     *
     * @param addressBook
     * 		an address book
     */
    public void setAddressBook(final AddressBook addressBook) {
        setChild(ChildIndices.ADDRESS_BOOK, addressBook);
    }

    /**
     * Get the object containing miscellaneous round information.
     *
     * @return round data
     */
    public PlatformData getPlatformData() {
        return getChild(ChildIndices.PLATFORM_DATA);
    }

    /**
     * Set the object containing miscellaneous platform information.
     *
     * @param round
     * 		round data
     */
    public void setPlatformData(final PlatformData round) {
        setChild(ChildIndices.PLATFORM_DATA, round);
    }

    /**
     * Get the previous address book.
     */
    public AddressBook getPreviousAddressBook() {
        return getChild(ChildIndices.PREVIOUS_ADDRESS_BOOK);
    }

    /**
     * Set the previous address book.
     *
     * @param addressBook
     * 		an address book
     */
    public void setPreviousAddressBook(final AddressBook addressBook) {
        setChild(ChildIndices.PREVIOUS_ADDRESS_BOOK, addressBook);
    }
}
