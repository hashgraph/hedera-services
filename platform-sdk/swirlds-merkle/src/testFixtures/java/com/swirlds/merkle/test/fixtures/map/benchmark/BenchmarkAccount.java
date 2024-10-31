/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.merkle.test.fixtures.map.benchmark;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.merkle.utility.Keyed;
import java.io.IOException;

/**
 * An account simulated for an MerkleMap benchmark.
 */
public class BenchmarkAccount extends PartialMerkleLeaf implements Keyed<BenchmarkKey>, MerkleLeaf {

    private static final long CLASS_ID = 0x6b7ca4c97dbade4dL;

    /**
     * The balance of the account.
     */
    private long balance;

    /**
     * Random bytes.
     */
    private byte[] data;

    private BenchmarkKey key;

    public BenchmarkAccount() {}

    public BenchmarkAccount(final long balance, final byte[] data) {
        this.balance = balance;
        this.data = data;
    }

    protected BenchmarkAccount(final BenchmarkAccount that) {
        super(that);
        this.balance = that.balance;
        this.data = that.data;
        this.key = that.key;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BenchmarkAccount copy() {
        return new BenchmarkAccount(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        balance = in.readLong();
        data = in.readByteArray(Integer.MAX_VALUE);
        key = in.readSerializable(false, BenchmarkKey::new);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(balance);
        out.writeByteArray(data);
        out.writeSerializable(key, false);
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
        return 1;
    }

    /**
     * Get the balance of the account.
     */
    public long getBalance() {
        return balance;
    }

    /**
     * Set the balance of the account.
     */
    public void setBalance(final long balance) {
        this.balance = balance;
    }

    /**
     * Get the data contained by the account.
     */
    public byte[] getData() {
        return data;
    }

    /**
     * Set the data contained by the account.
     */
    public void setData(final byte[] data) {
        this.data = data;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BenchmarkKey getKey() {
        return key;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKey(final BenchmarkKey key) {
        this.key = key;
    }
}
