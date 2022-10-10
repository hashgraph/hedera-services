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
package com.hedera.services.state.submerkle;

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenAssociation;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Process self serializable object that represents a This is useful for setting new token
 * associations created by the active transaction {@link ExpirableTxnRecord}.
 */
public class FcTokenAssociation implements SelfSerializable {

    static final int RELEASE_0180_VERSION = 1;
    static final int CURRENT_VERSION = RELEASE_0180_VERSION;

    static final long RUNTIME_CONSTRUCTABLE_ID = 0x41a2569130b01d2fL;

    private long tokenId;
    private long accountId;

    public FcTokenAssociation() {
        /* For RuntimeConstructable */
    }

    public FcTokenAssociation(final long tokenId, final long accountId) {
        this.tokenId = tokenId;
        this.accountId = accountId;
    }

    public long token() {
        return tokenId;
    }

    public long account() {
        return accountId;
    }

    @Override
    public boolean equals(final Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(FcTokenAssociation.class)
                .add("token", tokenId)
                .add("account", accountId)
                .toString();
    }

    public TokenAssociation toGrpc() {
        return TokenAssociation.newBuilder()
                .setAccountId(AccountID.newBuilder().setAccountNum(accountId).build())
                .setTokenId(TokenID.newBuilder().setTokenNum(tokenId).build())
                .build();
    }

    public static FcTokenAssociation fromGrpc(TokenAssociation tokenAssociation) {
        return new FcTokenAssociation(
                tokenAssociation.getTokenId().getTokenNum(),
                tokenAssociation.getAccountId().getAccountNum());
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version)
            throws IOException {
        tokenId = in.readLong();
        accountId = in.readLong();
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(tokenId);
        out.writeLong(accountId);
    }

    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }
}
