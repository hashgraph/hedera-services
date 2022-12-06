/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.state.virtual.entities;

import static com.hedera.node.app.service.mono.state.merkle.internals.BitPackUtils.unsignedLowOrder32From;

import com.hedera.node.app.service.mono.state.merkle.MerkleTokenRelStatus;
import com.hedera.node.app.service.mono.state.migration.HederaTokenRel;
import com.hedera.node.app.service.mono.state.virtual.annotations.StateSetter;
import com.hedera.node.app.service.mono.state.virtual.utils.CheckedConsumer;
import com.hedera.node.app.service.mono.state.virtual.utils.CheckedSupplier;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.virtualmap.VirtualValue;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class OnDiskTokenRel implements VirtualValue, HederaTokenRel {
    private static final int CURRENT_VERSION = 1;
    private static final long CLASS_ID = 0xc18c86c499e60727L;

    private long prev;
    private long next;
    private long numbers;
    private long balance;
    private byte flags;

    private boolean immutable = false;

    public OnDiskTokenRel() {
        // Intentional no-op
    }

    public OnDiskTokenRel(final OnDiskTokenRel that) {
        this.prev = that.prev;
        this.next = that.next;
        this.numbers = that.numbers;
        this.balance = that.balance;
        this.flags = that.flags;
    }

    public static OnDiskTokenRel from(final MerkleTokenRelStatus inMemoryTokenRel) {
        final var onDisk = new OnDiskTokenRel();
        onDisk.setPrev(inMemoryTokenRel.getPrev());
        onDisk.setNext(inMemoryTokenRel.getNext());
        onDisk.setNumbers(inMemoryTokenRel.getKey().value());
        onDisk.setBalance(inMemoryTokenRel.getBalance());
        onDisk.setFrozen(inMemoryTokenRel.isFrozen());
        onDisk.setKycGranted(inMemoryTokenRel.isKycGranted());
        onDisk.setAutomaticAssociation(inMemoryTokenRel.isAutomaticAssociation());
        return onDisk;
    }

    public static int serializedSizeInBytes() {
        // Why does (1 + 4 * Long.SIZE) result in "leaked keys"?
        return DataFileCommon.VARIABLE_DATA_SIZE;
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public boolean isImmutable() {
        return immutable;
    }

    @Override
    public OnDiskTokenRel copy() {
        this.immutable = true;
        return new OnDiskTokenRel(this);
    }

    @Override
    public VirtualValue asReadOnly() {
        final var copy = new OnDiskTokenRel(this);
        copy.immutable = true;
        return copy;
    }

    @Override
    public void serialize(final ByteBuffer to) throws IOException {
        serializeTo(to::put, to::putLong);
    }

    @Override
    public void deserialize(final ByteBuffer from, final int version) throws IOException {
        deserializeFrom(from::get, from::getLong);
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version)
            throws IOException {
        deserializeFrom(in::readByte, in::readLong);
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        serializeTo(out::writeByte, out::writeLong);
    }

    public long getNumbers() {
        return numbers;
    }

    @StateSetter
    public void setNumbers(final long numbers) {
        throwIfImmutable("Tried to set numbers on an immutable OnDiskTokenRel");
        this.numbers = numbers;
    }

    // Implementation of HederaTokenRel
    @Override
    public long getBalance() {
        return balance;
    }

    @Override
    @StateSetter
    public void setBalance(final long balance) {
        throwIfImmutable("Tried to set balance on an immutable OnDiskTokenRel");
        this.balance = balance;
    }

    @Override
    public boolean isFrozen() {
        return (flags & Masks.IS_FROZEN) != 0;
    }

    @Override
    @StateSetter
    public void setFrozen(final boolean flag) {
        throwIfImmutable("Tried to set IS_FROZEN on an immutable OnDiskTokenRel");
        if (flag) {
            flags |= Masks.IS_FROZEN;
        } else {
            flags &= ~Masks.IS_FROZEN;
        }
    }

    @Override
    public boolean isKycGranted() {
        return (flags & Masks.IS_KYC_GRANTED) != 0;
    }

    @Override
    @StateSetter
    public void setKycGranted(final boolean flag) {
        throwIfImmutable("Tried to set IS_KYC_GRANTED on an immutable OnDiskTokenRel");
        if (flag) {
            flags |= Masks.IS_KYC_GRANTED;
        } else {
            flags &= ~Masks.IS_KYC_GRANTED;
        }
    }

    @Override
    public boolean isAutomaticAssociation() {
        return (flags & Masks.IS_AUTO_ASSOCIATION) != 0;
    }

    @Override
    @StateSetter
    public void setAutomaticAssociation(final boolean flag) {
        throwIfImmutable("Tried to set IS_AUTO_ASSOCIATION on an immutable OnDiskTokenRel");
        if (flag) {
            flags |= Masks.IS_AUTO_ASSOCIATION;
        } else {
            flags &= ~Masks.IS_AUTO_ASSOCIATION;
        }
    }

    @Override
    public long getRelatedTokenNum() {
        return unsignedLowOrder32From(numbers);
    }

    @Override
    public EntityNumPair getKey() {
        return new EntityNumPair(numbers);
    }

    @Override
    @StateSetter
    public void setKey(final EntityNumPair numbers) {
        throwIfImmutable("Tried to set numbers on an immutable OnDiskTokenRel");
        this.numbers = numbers.value();
    }

    @Override
    public long getPrev() {
        return prev;
    }

    @Override
    public long getNext() {
        return next;
    }

    @Override
    @StateSetter
    public void setPrev(final long prev) {
        throwIfImmutable("Tried to set prev on an immutable OnDiskTokenRel");
        this.prev = prev;
    }

    @Override
    @StateSetter
    public void setNext(final long next) {
        throwIfImmutable("Tried to set next on an immutable OnDiskTokenRel");
        this.next = next;
    }

    private void serializeTo(
            final CheckedConsumer<Byte> writeByteFn, final CheckedConsumer<Long> writeLongFn)
            throws IOException {
        writeByteFn.accept(flags);
        writeLongFn.accept(prev);
        writeLongFn.accept(next);
        writeLongFn.accept(balance);
        writeLongFn.accept(numbers);
    }

    private void deserializeFrom(
            final CheckedSupplier<Byte> readByteFn, final CheckedSupplier<Long> readLongFn)
            throws IOException {
        throwIfImmutable();
        flags = readByteFn.get();
        prev = readLongFn.get();
        next = readLongFn.get();
        balance = readLongFn.get();
        numbers = readLongFn.get();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final OnDiskTokenRel that = (OnDiskTokenRel) o;
        return prev == that.prev
                && next == that.next
                && numbers == that.numbers
                && balance == that.balance
                && flags == that.flags;
    }

    @Override
    public int hashCode() {
        return Objects.hash(prev, next, numbers, balance, flags);
    }

    private static final class Masks {
        private static final byte IS_FROZEN = 1;
        private static final byte IS_KYC_GRANTED = 1 << 1;
        private static final byte IS_AUTO_ASSOCIATION = 1 << 2;
    }
}
