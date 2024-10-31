/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Random;

public final class AccountID implements SelfSerializable, FastCopyable {

    private static final long CLASS_ID = 0x2223a48b196f631eL;

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
        public static final int MIGRATE_TO_SERIALIZABLE = 2;
    }

    private long shardNum;
    private long realmNum;
    private long accountNum;
    private transient boolean deleted;

    private boolean immutable;

    public AccountID() {}

    AccountID(final long shardNum, final long realmNum, final long accountNum) {
        this.shardNum = shardNum;
        this.realmNum = realmNum;
        this.accountNum = accountNum;
    }

    private AccountID(final AccountID key) {
        this.shardNum = key.shardNum;
        this.realmNum = key.realmNum;
        this.accountNum = key.accountNum;
        this.deleted = key.deleted;
    }

    /**
     * Get an exact copy of this object. Does not make the original immutable.
     */
    public AccountID deepCopy() {
        return new AccountID(shardNum, realmNum, accountNum);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AccountID copy() {
        throwIfImmutable();
        return new AccountID(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        if (this.deleted) {
            throw new IllegalStateException("Trying to serialize a deleted AccountId");
        }

        out.writeLong(this.shardNum);
        out.writeLong(this.realmNum);
        out.writeLong(this.accountNum);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream inStream, int version) throws IOException {
        this.shardNum = inStream.readLong();
        this.realmNum = inStream.readLong();
        this.accountNum = inStream.readLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean release() {
        this.deleted = true;
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof AccountID)) {
            return false;
        }

        final AccountID accountID = (AccountID) o;
        return shardNum == accountID.shardNum && realmNum == accountID.realmNum && accountNum == accountID.accountNum;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(shardNum, realmNum, accountNum);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("(%d, %d, %d)", this.shardNum, this.realmNum, this.accountNum);
    }

    /**
     * Generates a random AccountID based on a seed
     *
     * @return Random seed-based AccountID
     */
    static AccountID generateRandom(final Random random) {
        final long shardNum = random.nextLong();
        final long realmNum = random.nextLong();
        final long accountNum = random.nextLong();

        return new AccountID(shardNum, realmNum, accountNum);
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
        return ClassVersion.MIGRATE_TO_SERIALIZABLE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMinimumSupportedVersion() {
        return ClassVersion.MIGRATE_TO_SERIALIZABLE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isImmutable() {
        return this.immutable;
    }
}
