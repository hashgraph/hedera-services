/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import java.io.IOException;
import java.util.Objects;

public class InternalValue extends PartialMerkleLeaf implements MerkleLeaf {

    public static final long CLASS_ID = 0xab6aef6fe9e3ca40L;

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private long balance;
    private long expirationTime;
    private long autoRenewPeriod;
    private long senderThreshold;
    private long receiverThreshold;
    private String memo;
    private boolean isSmartContract;
    private boolean receiverSigRequired;

    public InternalValue() {}

    public InternalValue(
            final long balance,
            final long expirationTime,
            final long autoRenewPeriod,
            final long senderThreshold,
            final long receiverThreshold,
            final String memo,
            final boolean isSmartContract,
            final boolean receiverSigRequired) {
        this.balance = balance;
        this.expirationTime = expirationTime;
        this.autoRenewPeriod = autoRenewPeriod;
        this.senderThreshold = senderThreshold;
        this.receiverThreshold = receiverThreshold;
        this.memo = memo;
        this.isSmartContract = isSmartContract;
        this.receiverSigRequired = receiverSigRequired;
    }

    private InternalValue(final InternalValue internalValue) {
        super(internalValue);
        this.balance = internalValue.balance;
        this.expirationTime = internalValue.expirationTime;
        this.autoRenewPeriod = internalValue.autoRenewPeriod;
        this.senderThreshold = internalValue.senderThreshold;
        this.receiverThreshold = internalValue.receiverThreshold;
        this.memo = internalValue.memo;
        this.isSmartContract = internalValue.isSmartContract;
        this.receiverSigRequired = internalValue.receiverSigRequired;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InternalValue copy() {
        throwIfImmutable();
        return new InternalValue(this);
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
    public int getClassVersion() {
        return ClassVersion.ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        final InternalValue that = (InternalValue) other;
        return balance == that.balance
                && expirationTime == that.expirationTime
                && autoRenewPeriod == that.autoRenewPeriod
                && senderThreshold == that.senderThreshold
                && receiverThreshold == that.receiverThreshold
                && isSmartContract == that.isSmartContract
                && receiverSigRequired == that.receiverSigRequired
                && Objects.equals(memo, that.memo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(
                balance,
                expirationTime,
                autoRenewPeriod,
                senderThreshold,
                receiverThreshold,
                memo,
                isSmartContract,
                receiverSigRequired);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(SerializableDataOutputStream outStream) throws IOException {
        outStream.writeLong(this.balance);
        outStream.writeLong(this.expirationTime);
        outStream.writeLong(this.autoRenewPeriod);
        outStream.writeLong(this.senderThreshold);
        outStream.writeLong(this.receiverThreshold);
        outStream.writeBoolean(this.isSmartContract);
        outStream.writeBoolean(this.receiverSigRequired);

        final byte[] memoData = this.memo.getBytes();
        outStream.writeByteArray(memoData);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(SerializableDataInputStream inStream, int version) throws IOException {
        this.copyFromVersion1(inStream);
        this.copyFromMemoVersion1(inStream);
    }

    public void copyFromVersion1(SerializableDataInputStream inStream) throws IOException {
        this.balance = inStream.readLong();
        this.expirationTime = inStream.readLong();
        this.autoRenewPeriod = inStream.readLong();
        this.senderThreshold = inStream.readLong();
        this.receiverThreshold = inStream.readLong();
        this.isSmartContract = inStream.readBoolean();
        this.receiverSigRequired = inStream.readBoolean();
    }

    public void copyFromMemoVersion1(final SerializableDataInputStream inStream) throws IOException {
        final byte[] memoData = inStream.readByteArray(Integer.MAX_VALUE);
        this.memo = new String(memoData);
    }
}
