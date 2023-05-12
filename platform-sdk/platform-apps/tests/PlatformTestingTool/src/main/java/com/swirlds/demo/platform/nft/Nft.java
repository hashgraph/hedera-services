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

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.merkle.map.test.pta.MapKey;
import java.io.IOException;
import java.util.Objects;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * This simulates an NFT token that Hedera uses for its token services.
 * Is, of course, much simpler than Hedera's implementation.
 */
public class Nft extends PartialMerkleLeaf implements Keyed<NftId>, MerkleLeaf {

    private static final long CLASS_ID = 0x8ddac0bf4595cacfL;
    private static final int DATA_STRING_LIMIT = 1_000_000;
    private static final int VERSION_ORIGINAL = 1;

    private long shardNum;
    private long realmNum;
    private long tokenNum;

    private MapKey mapKey;
    private String serialNumber;

    private String memo;

    /**
     * Create a new Nft.
     */
    public Nft() {
        this.mapKey = new MapKey();
    }

    /**
     * Copy constructor.
     */
    private Nft(final Nft nft) {
        super(nft);
        setShardNum(nft.getShardNum());
        setRealmNum(nft.getRealmNum());
        setTokenNum(nft.getTokenNum());
        setMapKey(nft.getMapKey().copy());
        setMemo(nft.getMemo());
        setSerialNumber(nft.getSerialNumber());
        nft.setImmutable(true);
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(this.shardNum);
        out.writeLong(this.realmNum);
        out.writeLong(this.tokenNum);
        mapKey.serialize(out);
        out.writeNormalisedString(this.serialNumber);
        out.writeNormalisedString(this.memo);
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        this.shardNum = in.readLong();
        this.realmNum = in.readLong();
        this.tokenNum = in.readLong();
        this.mapKey.deserialize(in, this.mapKey.getVersion());
        this.serialNumber = in.readNormalisedString(DATA_STRING_LIMIT);
        this.memo = in.readNormalisedString(DATA_STRING_LIMIT);
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
        return VERSION_ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Nft copy() {
        return new Nft(this);
    }

    /**
     * Get a deep copy of this object.
     */
    public Nft deepCopy() {
        final Nft nft = new Nft();

        nft.setShardNum(getShardNum());
        nft.setRealmNum(getRealmNum());
        nft.setTokenNum(getTokenNum());

        nft.setMemo(getMemo());
        nft.setSerialNumber(getSerialNumber());
        nft.setMapKey(getMapKey().copy());

        return nft;
    }

    public long getShardNum() {
        return shardNum;
    }

    public void setShardNum(final long shardNum) {
        this.shardNum = shardNum;
    }

    public long getRealmNum() {
        return realmNum;
    }

    public void setRealmNum(final long realmNum) {
        this.realmNum = realmNum;
    }

    public long getTokenNum() {
        return tokenNum;
    }

    public void setTokenNum(final long tokenNum) {
        this.tokenNum = tokenNum;
    }

    /**
     * Set the owner of this token.
     *
     * @param mapKey
     * 		the owner's key
     */
    public void setMapKey(final MapKey mapKey) {
        this.mapKey = mapKey;
    }

    /**
     * Get the owner of this token.
     *
     * @return the owner's key, or null if the owner has not been specified
     */
    public MapKey getMapKey() {
        return this.mapKey;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(final String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(final String memo) {
        this.memo = memo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(this.shardNum, this.realmNum, this.tokenNum);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == null || obj.getClass() != Nft.class) {
            return false;
        }

        final Nft that = (Nft) obj;

        return new EqualsBuilder()
                .append(this.getShardNum(), that.getShardNum())
                .append(this.getRealmNum(), that.getRealmNum())
                .append(this.getTokenNum(), that.getTokenNum())
                .append(this.getMemo(), that.getMemo())
                .append(this.getSerialNumber(), that.getSerialNumber())
                .append(this.getMapKey(), that.getMapKey())
                .isEquals();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("shardNum", getShardNum())
                .append("realmNum", getRealmNum())
                .append("tokenNum", getTokenNum())
                .append("owner", getMapKey())
                .append("memo", getMemo())
                .append("serialNumber", getSerialNumber())
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NftId getKey() {
        return new NftId(shardNum, realmNum, tokenNum);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKey(final NftId key) {
        shardNum = key.getShardNum();
        realmNum = key.getRealmNum();
        tokenNum = key.getTokenNum();
    }
}
