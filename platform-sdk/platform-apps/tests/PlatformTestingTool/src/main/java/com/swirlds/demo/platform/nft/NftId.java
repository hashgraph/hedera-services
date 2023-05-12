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

package com.swirlds.demo.platform.nft;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.exceptions.InvalidVersionException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

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
    public int getVersion() {
        return VERSION_ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof NftId)) {
            return false;
        }

        final NftId tokenId = (NftId) o;
        return new EqualsBuilder()
                .append(shardNum, tokenId.shardNum)
                .append(realmNum, tokenId.realmNum)
                .append(tokenNum, tokenId.tokenNum)
                .isEquals();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(shardNum)
                .append(realmNum)
                .append(tokenNum)
                .hashCode();
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
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("ShardNum", shardNum)
                .append("RealmNum", realmNum)
                .append("TokenNum", tokenNum)
                .toString();
    }
}
