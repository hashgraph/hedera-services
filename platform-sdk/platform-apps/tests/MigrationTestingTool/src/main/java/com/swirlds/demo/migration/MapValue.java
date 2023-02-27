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

package com.swirlds.demo.migration;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.exceptions.IllegalChildIndexException;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.fcqueue.FCQueue;
import java.util.Random;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class MapValue extends PartialNaryMerkleInternal implements Keyed<AccountID>, MerkleInternal {

    private static final long CLASS_ID = 0x9a048d2898e96abcL;

    private static class ClassVersion {
        public static final int ORIGINAL = 2;
        /**
         * Remove the binary object from this leaf.
         */
        public static final int NO_BLOBS = 3;
    }

    private static class ChildIndices {
        public static final int MAP_KEY = 0;
        public static final int RECORDS = 1;
        public static final int INTERNAL_VALUE = 2;

        public static final int CHILD_COUNT = 3;
    }

    public MapValue() {
        setMapKey(new MapKey());
        setRecords(new FCQueue<>());
        setInternalValue(new InternalValue());
    }

    private MapValue(
            final AccountID key,
            final long balance,
            final long expirationTime,
            final long autoRenewPeriod,
            final long senderThreshold,
            final long receiverThreshold,
            final String memo,
            final boolean isSmartContract,
            final boolean receiverSigRequired,
            final FCQueue<TransactionRecord> records) {
        super();
        setMapKey(new MapKey(key));
        setInternalValue(new InternalValue(
                balance,
                expirationTime,
                autoRenewPeriod,
                senderThreshold,
                receiverThreshold,
                memo,
                isSmartContract,
                receiverSigRequired));
        setRecords(records);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumberOfChildren() {
        return ChildIndices.CHILD_COUNT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMinimumChildCount() {
        return ChildIndices.CHILD_COUNT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaximumChildCount() {
        return ChildIndices.CHILD_COUNT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean childHasExpectedType(int index, long childClassId) {
        switch (index) {
            case ChildIndices.MAP_KEY:
                return childClassId == MapKey.CLASS_ID;
            case ChildIndices.RECORDS:
                return childClassId == FCQueue.CLASS_ID;
            case ChildIndices.INTERNAL_VALUE:
                return childClassId == InternalValue.CLASS_ID;
            default:
                throw new IllegalChildIndexException(getMinimumChildCount(), getMaximumChildCount(), index);
        }
    }

    private MapKey getMapKey() {
        return getChild(ChildIndices.MAP_KEY);
    }

    private void setMapKey(MapKey mapKey) {
        setChild(ChildIndices.MAP_KEY, mapKey);
    }

    private InternalValue getInternalValue() {
        return getChild(ChildIndices.INTERNAL_VALUE);
    }

    private void setInternalValue(InternalValue internalValue) {
        setChild(ChildIndices.INTERNAL_VALUE, internalValue);
    }

    private FCQueue<TransactionRecord> getRecords() {
        return getChild(ChildIndices.RECORDS);
    }

    private void setRecords(FCQueue<TransactionRecord> records) {
        setChild(ChildIndices.RECORDS, records);
    }

    private MapValue(final MapValue mapValue) {
        super();
        if (mapValue.getMapKey() != null) {
            setMapKey(mapValue.getMapKey().copy());
        }
        if (mapValue.getInternalValue() != null) {
            setInternalValue(mapValue.getInternalValue().copy());
        }
        if (mapValue.getRecords() != null) {
            setRecords(mapValue.getRecords().copy());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MapValue copy() {
        throwIfImmutable();
        return new MapValue(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof final MapValue mapValue)) {
            return false;
        }

        return new EqualsBuilder()
                .append(this.getInternalValue(), mapValue.getInternalValue())
                .append(this.getMapKey(), mapValue.getMapKey())
                .append(this.getRecords(), mapValue.getRecords())
                .isEquals();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(this.getMapKey())
                .append(this.getInternalValue())
                .append(this.getRecords())
                .toHashCode();
    }

    static MapValue generateRandom(final Random random, final AccountID key) {
        final long balance = random.nextLong();
        final long expirationtime = random.nextLong();
        final long autoRenewPeriod = random.nextLong();
        final long senderThreshold = random.nextLong();
        final long receiverThreshold = random.nextLong();
        final boolean isSmartContract = random.nextBoolean();
        final boolean receiverSigRequired = random.nextBoolean();
        final byte[] string = new byte[50];
        random.nextBytes(string);
        final String memo = new String(string);
        final byte[] internalData = new byte[100];
        random.nextBytes(internalData);
        final FCQueue<TransactionRecord> records = generateRandomFCQ(random);
        return new MapValue(
                key,
                balance,
                expirationtime,
                autoRenewPeriod,
                senderThreshold,
                receiverThreshold,
                memo,
                isSmartContract,
                receiverSigRequired,
                records);
    }

    public static FCQueue<TransactionRecord> generateRandomFCQ(final Random random) {
        final FCQueue<TransactionRecord> records = new FCQueue<>();
        for (int index = 0; index < 10; index++) {
            records.add(TransactionRecord.generateRandom(random));
        }

        return records;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AccountID getKey() {
        return ((MapKey) getChild(ChildIndices.MAP_KEY)).getAccountId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKey(final AccountID accountID) {
        if (getChild(ChildIndices.MAP_KEY) == null) {
            setChild(ChildIndices.MAP_KEY, new MapKey(accountID));
        } else {
            getMapKey().setAccountId(accountID);
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
        return ClassVersion.NO_BLOBS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMinimumSupportedVersion() {
        return ClassVersion.NO_BLOBS;
    }
}
