/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.legacy.proto.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.services.state.migration.QueryableRecords.NO_QUERYABLE_RECORDS;

import com.google.common.primitives.Ints;
import com.hedera.services.state.migration.QueryableRecords;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.utils.EntityNum;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.fcqueue.FCQueue;
import java.io.IOException;

public class MerklePayerRecords extends PartialMerkleLeaf implements Keyed<EntityNum>, MerkleLeaf {
    private static final int CURRENT_VERSION = 1;
    private static final long RUNTIME_CONSTRUCTABLE_ID = 0x0185e6fd3ab81c31L;

    private static final FCQueue<ExpirableTxnRecord> IMMUTABLE_EMPTY_FCQ = new FCQueue<>();

    static {
        IMMUTABLE_EMPTY_FCQ.copy();
    }

    private int num;
    private FCQueue<ExpirableTxnRecord> payerRecords = null;

    public MerklePayerRecords() {
        // RuntimeConstructable
    }

    public MerklePayerRecords(final MerklePayerRecords that) {
        this.num = that.num;
        this.payerRecords = (that.payerRecords == null) ? null : that.payerRecords.copy();
    }

    @Override
    public boolean isSelfHashing() {
        return true;
    }

    @Override
    public Hash getHash() {
        final var recordsHash = readOnlyQueue().getHash();
        final var recordsHashLen = recordsHash.getValue().length;
        final byte[] bytes = new byte[recordsHashLen + 4];
        System.arraycopy(Ints.toByteArray(num), 0, bytes, 0, 4);
        System.arraycopy(recordsHash.getValue(), 0, bytes, 4, recordsHashLen);
        return new Hash(noThrowSha384HashOf(bytes), DigestType.SHA_384);
    }

    @Override
    public MerklePayerRecords copy() {
        setImmutable(true);
        return new MerklePayerRecords(this);
    }

    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version)
            throws IOException {
        throwIfImmutable();
        num = in.readInt();
        payerRecords = in.readSerializable(true, FCQueue::new);
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeInt(num);
        out.writeSerializable(payerRecords, true);
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public EntityNum getKey() {
        return EntityNum.fromInt(num);
    }

    @Override
    public void setKey(final EntityNum num) {
        this.num = num.intValue();
    }

    public void offer(final ExpirableTxnRecord payerRecord) {
        ensureUsable();
        payerRecords.offer(payerRecord);
    }

    public FCQueue<ExpirableTxnRecord> mutableQueue() {
        ensureUsable();
        return payerRecords;
    }

    public FCQueue<ExpirableTxnRecord> readOnlyQueue() {
        return (payerRecords == null) ? IMMUTABLE_EMPTY_FCQ : payerRecords;
    }

    public QueryableRecords asQueryableRecords() {
        return (payerRecords == null)
                ? NO_QUERYABLE_RECORDS
                : new QueryableRecords(payerRecords.size(), payerRecords.iterator());
    }

    private void ensureUsable() {
        if (payerRecords == null) {
            payerRecords = new FCQueue<>();
        }
    }
}
