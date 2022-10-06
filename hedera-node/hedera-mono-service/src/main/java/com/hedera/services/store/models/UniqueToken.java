/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.models;

import com.google.common.base.MoreObjects;
import com.hedera.services.state.submerkle.RichInstant;

/**
 * Encapsulates the state and operations of a Hedera Unique token.
 *
 * <p>Operations are validated, and throw a {@link
 * com.hedera.services.exceptions.InvalidTransactionException} with response code capturing the
 * failure when one occurs.
 */
public class UniqueToken {
    private Id tokenId;
    private long serialNumber;
    private RichInstant creationTime;
    private Id owner;
    private Id spender;
    private byte[] metadata;
    private NftId nftId;

    public UniqueToken(Id tokenId, long serialNumber) {
        this.tokenId = tokenId;
        this.serialNumber = serialNumber;
        this.nftId = new NftId(tokenId.shard(), tokenId.realm(), tokenId.num(), serialNumber);
    }

    public UniqueToken(Id tokenId, long serialNumber, Id owner) {
        this.tokenId = tokenId;
        this.serialNumber = serialNumber;
        this.owner = owner;
        this.nftId = new NftId(tokenId.shard(), tokenId.realm(), tokenId.num(), serialNumber);
    }

    public UniqueToken(
            Id tokenId, long serialNumber, RichInstant creationTime, Id owner, byte[] metadata) {
        this.tokenId = tokenId;
        this.serialNumber = serialNumber;
        this.creationTime = creationTime;
        this.owner = owner;
        this.metadata = metadata;
        this.nftId = new NftId(tokenId.shard(), tokenId.realm(), tokenId.num(), serialNumber);
    }

    public NftId getNftId() {
        return nftId;
    }

    public Id getTokenId() {
        return tokenId;
    }

    // only used in unit tests
    public void setTokenId(Id tokenId) {
        this.tokenId = tokenId;
    }

    public long getSerialNumber() {
        return serialNumber;
    }

    // only used in unit tests
    public void setSerialNumber(long serialNumber) {
        this.serialNumber = serialNumber;
    }

    public RichInstant getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(RichInstant creationTime) {
        this.creationTime = creationTime;
    }

    public Id getOwner() {
        return owner;
    }

    public void setOwner(Id owner) {
        this.owner = owner;
    }

    public Id getSpender() {
        return spender;
    }

    public void setSpender(Id spender) {
        this.spender = spender;
    }

    public byte[] getMetadata() {
        return metadata;
    }

    public void setMetadata(byte[] metadata) {
        this.metadata = metadata;
    }

    public void clearSpender() {
        this.spender = Id.DEFAULT;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("tokenID", tokenId)
                .add("serialNum", serialNumber)
                .add("metadata", metadata)
                .add("creationTime", creationTime)
                .add("owner", owner)
                .add("spender", spender)
                .toString();
    }
}
