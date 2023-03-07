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

package com.swirlds.demo.platform.nft;

import com.swirlds.common.io.exceptions.InvalidVersionException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import java.io.IOException;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * A unique identifier for an {@link Nft}.
 */
public class MerkleNftId extends PartialMerkleLeaf implements MerkleLeaf {

    public static final long CLASS_ID = 0xaf558e8b56087253L;

    private static final int VERSION_ORIGINAL = 1;

    // These values are intentionally stored as primitives to avoid the overhead of an internal object.
    private long shardNum;
    private long realmNum;
    private long tokenNum;

    /**
     * Create a new token ID.
     */
    public MerkleNftId() {}

    /**
     * Create a new token ID.
     *
     * @param nftId
     * 		the ID of the NFT
     */
    public MerkleNftId(final NftId nftId) {
        this.shardNum = nftId.getShardNum();
        this.realmNum = nftId.getRealmNum();
        this.tokenNum = nftId.getTokenNum();
    }

    /**
     * Create a new token ID.
     *
     * @param shardNum
     * 		the shard number
     * @param realmNum
     * 		the realm number
     * @param tokenNum
     * 		the token number
     */
    public MerkleNftId(final long shardNum, final long realmNum, final long tokenNum) {
        this.shardNum = shardNum;
        this.realmNum = realmNum;
        this.tokenNum = tokenNum;
    }

    /**
     * Copy constructor.
     */
    private MerkleNftId(final MerkleNftId nftId) {
        super(nftId);
        setNftId(nftId.getNftId());
        nftId.setImmutable(true);
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

        final NftId nftId = in.readSerializable(false, NftId::new);
        setNftId(nftId);
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
    public MerkleNftId copy() {
        return new MerkleNftId(this);
    }

    /**
     * Create a deep copy. Does not care about mutability status, and will not make the original immutable.
     */
    public MerkleNftId deepCopy() {
        return new MerkleNftId(shardNum, realmNum, tokenNum);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof MerkleNftId)) {
            return false;
        }

        final MerkleNftId tokenId = (MerkleNftId) o;
        return new EqualsBuilder()
                .append(shardNum, tokenId.shardNum)
                .append(realmNum, tokenId.realmNum)
                .append(tokenNum, tokenId.tokenNum)
                .isEquals();
    }

    /**
     * Get the identifying key for an NFT.
     */
    public NftId getNftId() {
        return new NftId(shardNum, realmNum, tokenNum);
    }

    /**
     * Set the identifying key for an NFT.
     */
    public void setNftId(final NftId nftId) {
        shardNum = nftId.getShardNum();
        realmNum = nftId.getRealmNum();
        tokenNum = nftId.getTokenNum();
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
