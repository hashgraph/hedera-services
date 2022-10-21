/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.merkle;

import com.google.common.base.MoreObjects;
import com.hedera.services.utils.MiscUtils;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import java.io.IOException;

public class MerkleEntityId extends PartialMerkleLeaf implements MerkleLeaf {
    static final int MERKLE_VERSION = 1;
    static final long RUNTIME_CONSTRUCTABLE_ID = 0xd5dd2ebaa0bde03L;

    private long shard;
    private long realm;
    private long num;

    public MerkleEntityId() {}

    public MerkleEntityId(final long shard, final long realm, final long num) {
        this.shard = shard;
        this.realm = realm;
        this.num = num;
    }

    /* --- MerkleLeaf --- */
    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public int getVersion() {
        return MERKLE_VERSION;
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version)
            throws IOException {
        shard = in.readLong();
        realm = in.readLong();
        num = in.readLong();
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(shard);
        out.writeLong(realm);
        out.writeLong(num);
    }

    /* --- Object --- */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || MerkleEntityId.class != o.getClass()) {
            return false;
        }

        final var that = (MerkleEntityId) o;
        return this.shard == that.shard && this.realm == that.realm && this.num == that.num;
    }

    @Override
    public int hashCode() {
        /* Until realms are implemented, only the entity number distinguishes this key from any other. */
        return (int) MiscUtils.perm64(num);
    }

    /* --- FastCopyable --- */
    @Override
    public MerkleEntityId copy() {
        setImmutable(true);
        return new MerkleEntityId(shard, realm, num);
    }

    /* --- Bean --- */
    public long getShard() {
        return shard;
    }

    public void setShard(final long shard) {
        throwIfImmutable("Cannot change this entity's shard if it's immutable.");
        this.shard = shard;
    }

    public long getRealm() {
        return realm;
    }

    public void setRealm(final long realm) {
        throwIfImmutable("Cannot change this entity's realm if it's immutable.");
        this.realm = realm;
    }

    public long getNum() {
        return num;
    }

    public void setNum(final long num) {
        throwIfImmutable("Cannot change this entity's number if it's immutable.");
        this.num = num;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("shard", shard)
                .add("realm", realm)
                .add("entity", num)
                .toString();
    }
}
