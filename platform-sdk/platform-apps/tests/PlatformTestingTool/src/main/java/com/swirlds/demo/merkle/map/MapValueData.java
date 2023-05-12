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

package com.swirlds.demo.merkle.map;

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.merkle.map.test.pta.MapKey;
import com.swirlds.merkle.map.test.pta.MapValue;
import java.io.IOException;
import java.io.Serializable;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class MapValueData extends PartialMerkleLeaf implements Keyed<MapKey>, MapValue, Serializable, MerkleLeaf {

    public static final long CLASS_ID = 0x206bc63a03b16c28L;

    private MapKey key;

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
        public static final int UID = 2;
    }

    private long balance;
    private long sendThresholdValue;
    private long receiveThresholdValue;
    private boolean receiverSignatureRequired;
    private long uid;

    public MapValueData() {}

    public MapValueData(
            final long balance,
            final long sendThresholdValue,
            final long receiveThresholdValue,
            final boolean isReceiverSignatureRequired,
            final long uid) {
        this.balance = balance;
        this.sendThresholdValue = sendThresholdValue;
        this.receiveThresholdValue = receiveThresholdValue;
        this.receiverSignatureRequired = isReceiverSignatureRequired;
        this.uid = uid;
    }

    private MapValueData(final MapValueData sourceValue) {
        super(sourceValue);
        this.balance = sourceValue.getBalance();
        this.sendThresholdValue = sourceValue.getSendThresholdValue();
        this.receiveThresholdValue = sourceValue.getReceiveThresholdValue();
        this.receiverSignatureRequired = sourceValue.isReceiverSignatureRequired();
        this.uid = sourceValue.getUid();
        setKey(sourceValue.getKey().copy());
        setImmutable(false);
        sourceValue.setImmutable(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MapKey getKey() {
        return key;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKey(final MapKey key) {
        this.key = key;
    }

    public long getBalance() {
        return this.balance;
    }

    public void setBalance(long balance) {
        invalidateHash();
        this.balance = balance;
    }

    public long getSendThresholdValue() {
        return this.sendThresholdValue;
    }

    public void setSendThresholdValue(long sendThresholdValue) {
        invalidateHash();
        this.sendThresholdValue = sendThresholdValue;
    }

    public long getReceiveThresholdValue() {
        return this.receiveThresholdValue;
    }

    public void setReceiveThresholdValue(long receiveThreshold) {
        invalidateHash();
        this.receiveThresholdValue = receiveThreshold;
    }

    public boolean isReceiverSignatureRequired() {
        return this.receiverSignatureRequired;
    }

    public void setReceiverSignatureRequired(boolean required) {
        invalidateHash();
        this.receiverSignatureRequired = required;
    }

    @Override
    public Hash calculateHash() {
        if (getHash() != null) {
            return getHash();
        }

        CryptographyHolder.get().digestSync(this);
        return getHash();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final MapValueData mapValue = (MapValueData) o;
        return new EqualsBuilder()
                .append(balance, mapValue.balance)
                .append(sendThresholdValue, mapValue.sendThresholdValue)
                .append(receiveThresholdValue, mapValue.receiveThresholdValue)
                .append(receiverSignatureRequired, mapValue.receiverSignatureRequired)
                .append(uid, mapValue.uid)
                .build();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(balance)
                .append(sendThresholdValue)
                .append(receiveThresholdValue)
                .append(receiverSignatureRequired)
                .append(uid)
                .build();
    }

    public String toString() {
        return "MapValue{" + "balance="
                + balance + ", sendThresholdValue="
                + sendThresholdValue + ", receiveThresholdValue="
                + receiveThresholdValue + ", receiverSignatureRequired="
                + receiverSignatureRequired + '}';
    }

    @Override
    public MapValueData copy() {
        throwIfImmutable();
        return new MapValueData(this);
    }

    @Override
    public void serialize(SerializableDataOutputStream outStream) throws IOException {
        outStream.writeLong(this.balance);
        outStream.writeLong(this.sendThresholdValue);
        outStream.writeLong(this.receiveThresholdValue);
        outStream.writeBoolean(this.receiverSignatureRequired);
        outStream.writeLong(this.uid);
        outStream.writeSerializable(key, false);
    }

    @Override
    public void deserialize(SerializableDataInputStream inStream, int version) throws IOException {
        this.balance = inStream.readLong();
        this.sendThresholdValue = inStream.readLong();
        this.receiveThresholdValue = inStream.readLong();
        this.receiverSignatureRequired = inStream.readBoolean();
        if (version == ClassVersion.UID) {
            this.uid = inStream.readLong();
        }
        key = inStream.readSerializable(false, MapKey::new);
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return ClassVersion.UID;
    }

    @Override
    public long getUid() {
        return uid;
    }
}
