/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.demo.migration.virtual;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualValue;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * This class represents an account being store inside
 * a {@link com.swirlds.virtualmap.VirtualMap} instance.
 */
public class AccountVirtualMapValue implements VirtualValue {
    private static final long CLASS_ID = 0xd68a5aec20392ff5L;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private long balance;
    private long sendThreshold;
    private long receiveThreshold;
    private boolean requireSignature;
    private long uid;

    public AccountVirtualMapValue() {
        this(0, 0, 0, false, 0);
    }

    public AccountVirtualMapValue(
            final long balance,
            final long sendThreshold,
            final long receiveThreshold,
            final boolean requireSignature,
            final long uid) {
        this.balance = balance;
        this.sendThreshold = sendThreshold;
        this.receiveThreshold = receiveThreshold;
        this.requireSignature = requireSignature;
        this.uid = uid;
    }

    public AccountVirtualMapValue(AccountVirtualMapValue accountVirtualMapValue) {
        this.balance = accountVirtualMapValue.balance;
        this.sendThreshold = accountVirtualMapValue.sendThreshold;
        this.receiveThreshold = accountVirtualMapValue.receiveThreshold;
        this.requireSignature = accountVirtualMapValue.requireSignature;
        this.uid = accountVirtualMapValue.uid;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getClassVersion() {
        return ClassVersion.ORIGINAL;
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
        out.writeLong(balance);
        out.writeLong(sendThreshold);
        out.writeLong(receiveThreshold);
        out.write(getRequireSignatureAsByte());
        out.writeLong(uid);
    }

    void serialize(final WritableSequentialData out) {
        out.writeLong(balance);
        out.writeLong(sendThreshold);
        out.writeLong(receiveThreshold);
        out.writeByte(getRequireSignatureAsByte());
        out.writeLong(uid);
    }

    @Deprecated
    void serialize(final ByteBuffer buffer) {
        buffer.putLong(balance);
        buffer.putLong(sendThreshold);
        buffer.putLong(receiveThreshold);
        buffer.put(getRequireSignatureAsByte());
        buffer.putLong(uid);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        this.balance = in.readLong();
        this.sendThreshold = in.readLong();
        this.receiveThreshold = in.readLong();
        this.requireSignature = in.readByte() == 1;
        this.uid = in.readLong();
    }

    void deserialize(final ReadableSequentialData in) {
        this.balance = in.readLong();
        this.sendThreshold = in.readLong();
        this.receiveThreshold = in.readLong();
        this.requireSignature = in.readByte() == 1;
        this.uid = in.readLong();
    }

    @Deprecated
    void deserialize(final ByteBuffer buffer, final int version) {
        this.balance = buffer.getLong();
        this.sendThreshold = buffer.getLong();
        this.receiveThreshold = buffer.getLong();
        this.requireSignature = buffer.get() == 1;
        this.uid = buffer.getLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VirtualValue copy() {
        return new AccountVirtualMapValue(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VirtualValue asReadOnly() {
        return new AccountVirtualMapValue(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "AccountVirtualMapValue{" + "balance="
                + balance + ", sendThreshold="
                + sendThreshold + ", receiveThreshold="
                + receiveThreshold + ", requireSignature="
                + requireSignature + ", uid="
                + uid + '}';
    }

    /**
     * @return Return {@code 1} if {@code requireSignature} is true, {@code 0} otherwise.
     */
    private byte getRequireSignatureAsByte() {
        return (byte) (requireSignature ? 1 : 0);
    }

    /**
     * @return Return the balance of the account.
     */
    public long getBalance() {
        return balance;
    }

    /**
     * @return Return the {@code sendThreshold} of the account.
     */
    public long getSendThreshold() {
        return sendThreshold;
    }

    /**
     * @return Return the {@code receiveThreshold} of the account.
     */
    public long getReceiveThreshold() {
        return receiveThreshold;
    }

    /**
     * @return Return the {@code requireSignature} of the account.
     */
    public boolean isRequireSignature() {
        return requireSignature;
    }

    /**
     * @return Return the {@code uid} of the account.
     */
    public long getUid() {
        return uid;
    }

    /**
     * @return The total size in bytes of all the fields of this class.
     */
    public static int getSizeInBytes() {
        return 4 * Long.BYTES + 1;
    }
}
