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

public class MerkleScheduledTransactionsState extends PartialMerkleLeaf implements MerkleLeaf {
    public static final int RELEASE_0270_VERSION = 1;
    static final int CURRENT_VERSION = RELEASE_0270_VERSION;

    static final long RUNTIME_CONSTRUCTABLE_ID = 0x174cca55254e7f12L;

    /** The current minimum second stored in the {@link MerkleScheduledTransactions} children. */
    private long currentMinSecond = Long.MAX_VALUE;

    public MerkleScheduledTransactionsState() {
        /* RuntimeConstructable */
    }

    public MerkleScheduledTransactionsState(MerkleScheduledTransactionsState toCopy) {
        this.currentMinSecond = toCopy.currentMinSecond;
    }

    public MerkleScheduledTransactionsState(final long currentMinSecond) {
        this.currentMinSecond = currentMinSecond;
    }

    /* --- MerkleLeaf --- */
    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        currentMinSecond = in.readLong();
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeLong(currentMinSecond);
    }

    /* --- Copyable --- */
    public MerkleScheduledTransactionsState copy() {
        setImmutable(true);
        return new MerkleScheduledTransactionsState(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || MerkleScheduledTransactionsState.class != o.getClass()) {
            return false;
        }

        var that = (MerkleScheduledTransactionsState) o;

        return this.currentMinSecond == that.currentMinSecond;
    }

    @Override
    public int hashCode() {
        return (int) MiscUtils.perm64(currentMinSecond);
    }

    /* --- Bean --- */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("currentMinSecond", currentMinSecond)
                .toString();
    }

    public long currentMinSecond() {
        return currentMinSecond;
    }

    public void setCurrentMinSecond(long currentMinSecond) {
        throwIfImmutable(
                "Cannot change this MerkleScheduledTransactionsState's currentMinSecond if it's"
                        + " immutable.");
        this.currentMinSecond = currentMinSecond;
    }
}
