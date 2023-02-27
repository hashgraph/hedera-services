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

package com.swirlds.demo.migration.virtual;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * This class represents the key to find an account that is being
 * stored inside a {@link com.swirlds.virtualmap.VirtualMap} instance.
 */
public class AccountVirtualMapKey implements VirtualKey<AccountVirtualMapKey> {
    private static final long CLASS_ID = 0xff95b64a8d311cdaL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private long realmID;
    private long shardId;
    private long accountID;

    public AccountVirtualMapKey() {
        this(0, 0, 0);
    }

    public AccountVirtualMapKey(final long realmID, final long shardId, final long accountID) {
        this.realmID = realmID;
        this.shardId = shardId;
        this.accountID = accountID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
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
        out.writeLong(realmID);
        out.writeLong(shardId);
        out.writeLong(accountID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        this.realmID = in.readLong();
        this.shardId = in.readLong();
        this.accountID = in.readLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final ByteBuffer buffer) throws IOException {
        buffer.putLong(realmID);
        buffer.putLong(shardId);
        buffer.putLong(accountID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final ByteBuffer buffer, final int version) throws IOException {
        this.realmID = buffer.getLong();
        this.shardId = buffer.getLong();
        this.accountID = buffer.getLong();
    }

    public boolean equals(final ByteBuffer buffer, final int version) throws IOException {
        return realmID == buffer.getLong() && shardId == buffer.getLong() && accountID == buffer.getLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(final AccountVirtualMapKey other) {
        int order = Long.compare(realmID, other.realmID);
        if (order != 0) {
            return order;
        }

        order = Long.compare(shardId, other.shardId);
        if (order != 0) {
            return order;
        }

        return Long.compare(accountID, other.accountID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "AccountVirtualMapKey{" + "realmID="
                + realmID + ", shardId="
                + shardId + ", accountID="
                + accountID + '}';
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final AccountVirtualMapKey that = (AccountVirtualMapKey) o;

        return new EqualsBuilder()
                .append(realmID, that.realmID)
                .append(shardId, that.shardId)
                .append(accountID, that.accountID)
                .isEquals();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(realmID)
                .append(shardId)
                .append(accountID)
                .toHashCode();
    }

    public static int getSizeInBytes() {
        return 3 * Long.BYTES;
    }
}
