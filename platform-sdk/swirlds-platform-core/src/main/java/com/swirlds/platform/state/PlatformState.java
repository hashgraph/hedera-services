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

import static com.swirlds.virtualmap.VirtualMap.ClassVersion.ORIGINAL;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialBinaryMerkleInternal;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.uptime.UptimeData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * This subtree contains state data which is managed and used exclusively by the platform.
 */
public class PlatformState extends PartialNaryMerkleInternal implements MerkleInternal {

    public static final long CLASS_ID = 0x483ae5404ad0d0bfL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
        public static final int UPTIME_DATA = 2;
    }

    private static final class ChildIndices {
        public static final int PLATFORM_DATA = 0;
        public static final int ADDRESS_BOOK = 1;
        public static final int UPTIME_DATA = 2;
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
        this.setUptimeData(that.getUptimeData().copy());
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
        return ClassVersion.UPTIME_DATA;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addDeserializedChildren(final @NonNull List<MerkleNode> children, final int version) {
        super.addDeserializedChildren(children, version);

        if (version == ORIGINAL) {
            setUptimeData(new UptimeData());
        }
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
     * Get the uptime data.
     */
    public @NonNull UptimeData getUptimeData() {
        return getChild(ChildIndices.UPTIME_DATA);
    }

    /**
     * Set the uptime data.
     */
    public void setUptimeData(@NonNull final UptimeData uptimeData) {
        setChild(ChildIndices.UPTIME_DATA, uptimeData);
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
     * Generates a one-line summary of important fields from the <code>PlatformState</code>, meant to be logged at
     * the
     * same time as a call to <code>MerkleHashChecker.generateHashDebugString()</code>.
     */
    public String getInfoString() {
        return getPlatformData().getInfoString(getAddressBook().getHash());
    }
}
