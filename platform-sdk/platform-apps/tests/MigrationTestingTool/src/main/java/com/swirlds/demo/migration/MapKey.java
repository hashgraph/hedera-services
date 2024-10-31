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

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import java.io.IOException;
import java.util.Objects;

public final class MapKey extends PartialMerkleLeaf implements MerkleLeaf {

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    public static final long CLASS_ID = 0x35c5acefca4a4fd4L;

    private AccountID accountId;

    public MapKey() {
        this.accountId = new AccountID();
    }

    public MapKey(final AccountID accountId) {
        this.accountId = accountId;
    }

    private MapKey(final MapKey mapKey) {
        super(mapKey);
        this.accountId = mapKey.accountId.copy();
        setImmutable(false);
        mapKey.setImmutable(true);
    }

    /**
     * Get the account ID.
     */
    public AccountID getAccountId() {
        return accountId;
    }

    /**
     * Set the account ID.
     */
    public void setAccountId(final AccountID accountId) {
        this.accountId = accountId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MapKey copy() {
        throwIfImmutable();
        return new MapKey(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof MapKey)) {
            return false;
        }

        final MapKey mapKey = (MapKey) o;
        return accountId.equals(mapKey.accountId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(accountId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return this.accountId.toString();
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
        return ClassVersion.ORIGINAL;
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeSerializable(this.accountId, true);
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        this.accountId = in.readSerializable();
    }
}
