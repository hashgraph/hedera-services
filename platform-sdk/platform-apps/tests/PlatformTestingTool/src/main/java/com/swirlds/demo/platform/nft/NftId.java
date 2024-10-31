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

package com.swirlds.demo.platform.nft;

import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.exceptions.InvalidVersionException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * A unique identifier for an {@link Nft}.
 */
public class NftId implements SelfSerializable {

    public static final long CLASS_ID = 0x217c9b397267453L;

    private static final int VERSION_ORIGINAL = 1;

    private long shardNum;
    private long realmNum;
    private long tokenNum;

    /**
     * Create a new token ID.
     */
    public NftId() {}

    /**
     * Create a new token ID.
     *
     * @param shardNum
     * 		the shard number for the token
     * @param realmNum
     * 		the realm number for the token
     * @param tokenNum
     * 		the token serial number
     */
    public NftId(final long shardNum, final long realmNum, final long tokenNum) {
        this.shardNum = shardNum;
        this.realmNum = realmNum;
        this.tokenNum = tokenNum;
    }

    /**
     * Copy constructor.
     */
    private NftId(final NftId nftId) {
        this.shardNum = nftId.shardNum;
        this.realmNum = nftId.realmNum;
        this.tokenNum = nftId.tokenNum;
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
        out.writeLong(this.shardNum);
        out.writeLong(this.realmNum);
        out.writeLong(this.tokenNum);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        if (version != VERSION_ORIGINAL) {
            throw new InvalidVersionException(VERSION_ORIGINAL, version);
        }

        this.shardNum = in.readLong();
        this.realmNum = in.readLong();
        this.tokenNum = in.readLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getClassVersion() {
        return VERSION_ORIGINAL;
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
        final NftId nftId = (NftId) other;
        return shardNum == nftId.shardNum && realmNum == nftId.realmNum && tokenNum == nftId.tokenNum;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(shardNum, realmNum, tokenNum);
    }

    /**
     * Get the shard number for this token.
     */
    public long getShardNum() {
        return shardNum;
    }

    /**
     * Get the realm number for this token.
     */
    public long getRealmNum() {
        return realmNum;
    }

    /**
     * Get the token's serial number.
     */
    public long getTokenNum() {
        return tokenNum;
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
        return new ToStringBuilder(this)
                .append("ShardNum", shardNum)
                .append("RealmNum", realmNum)
                .append("TokenNum", tokenNum)
                .toString();
    }
}
