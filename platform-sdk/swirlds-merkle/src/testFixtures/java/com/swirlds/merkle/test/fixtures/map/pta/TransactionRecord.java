/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.merkle.test.fixtures.map.pta;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.crypto.AbstractSerializableHashable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

public class TransactionRecord extends AbstractSerializableHashable implements FastCopyable, Serializable {
    private static final long CLASS_ID = 0xcdd5ad651cf2c4d8L;

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
        public static final int ADD_EXPIRATION_TIME = 2;
    }

    private static final int MAX_CONTENT_LENGTH = 1024 * 100;
    public static final int DEFAULT_EXPIRATION_TIME = 180;

    // In this version expirationTime is added
    private static final int EXPIRATION_VERSION = 2;

    private long index;

    private long balance;

    private byte[] content;

    private long expirationTime;

    private boolean immutable;

    public TransactionRecord() {}

    public TransactionRecord(final long index, final long balance, final byte[] content, final long expirationTime) {
        this.index = index;
        this.balance = balance;
        this.content = content;
        this.expirationTime = expirationTime;
    }

    private TransactionRecord(final TransactionRecord sourceTransaction) {
        this.index = sourceTransaction.index;
        this.balance = sourceTransaction.balance;
        if (sourceTransaction.content != null) {
            this.content = Arrays.copyOf(sourceTransaction.content, sourceTransaction.content.length);
        }

        this.expirationTime = sourceTransaction.expirationTime;
    }

    public long getIndex() {
        return index;
    }

    public long getBalance() {
        return balance;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    @Override
    public TransactionRecord copy() {
        throwIfImmutable();
        return new TransactionRecord(this);
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeLong(this.index);
        out.writeLong(this.balance);
        out.writeByteArray(this.content);
        out.writeLong(this.expirationTime);
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        this.index = in.readLong();
        this.balance = in.readLong();
        this.content = in.readByteArray(MAX_CONTENT_LENGTH);
        if (version >= EXPIRATION_VERSION) {
            this.expirationTime = in.readLong();
        }
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        final TransactionRecord that = (TransactionRecord) other;
        return index == that.index
                && balance == that.balance
                && expirationTime == that.expirationTime
                && Arrays.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(index, balance, expirationTime);
        result = 31 * result + Arrays.hashCode(content);
        return result;
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getClassVersion() {
        return ClassVersion.ADD_EXPIRATION_TIME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isImmutable() {
        return this.immutable;
    }
}
