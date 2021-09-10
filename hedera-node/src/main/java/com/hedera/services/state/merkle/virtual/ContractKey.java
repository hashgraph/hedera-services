package com.hedera.services.state.merkle.virtual;

import com.hedera.services.store.models.Id;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * The key of a key/value pair used by a Smart Contract for storage purposes.
 *
 * We only store the number part of the contract ID as the ideas ia there will be a virtual merkle tree for each shard
 * and realm.
 */
public final class ContractKey implements VirtualKey {
    public static final int SERIALIZED_SIZE = Long.BYTES + Integer.BYTES + ContractUint256.SERIALIZED_SIZE;
    private long contractId;
    private ContractUint256 key;

    public ContractKey() {
        // there has to be a default constructor for deserialize
    }

    public ContractKey(long contractId, ContractUint256 key) {
        this.contractId = contractId;
        this.key = key;
    }

    public long getContractId() {
        return contractId;
    }

    public ContractUint256 getKey() {
        return key;
    }

    @Override
    public long getClassId() {
        return 0xb2c0a1f733950abdL;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContractKey that = (ContractKey) o;
        return Objects.equals(contractId, that.contractId) && Objects.equals(key, that.key);
    }

    /**
     * Special hash to make sure we get good distribution.
     */
    @Override
    public int hashCode() {
//        long contractIdHash = contractId;
//        contractIdHash ^= contractIdHash >> 12;
//        contractIdHash ^= contractIdHash << 25;
//        contractIdHash ^= contractIdHash >> 27;
//        contractIdHash *= 0x2545F4914F6CDD1DL;
        int result = 1;
//        result = 31 * result + (int)contractIdHash;
        result = 31 * result + (int)(contractId ^ (contractId >>> 32));
        result = 31 * result + (key == null ? 0 : key.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return "ContractKey{id=" + contractId+", key=" +key +'}';
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int i) throws IOException {
        contractId = in.readLong();
        int version = in.readInt();
        key = new ContractUint256();
        key.deserialize(in,version);
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeLong(contractId);
        out.writeInt(key.getVersion());
        key.serialize(out);
    }

    @Override
    public void serialize(ByteBuffer byteBuffer) throws IOException {
        byteBuffer.putLong(contractId);
        byteBuffer.putInt(key.getVersion());
        key.serialize(byteBuffer);
    }

    @Override
    public void deserialize(ByteBuffer byteBuffer, int i) throws IOException {
        contractId = byteBuffer.getLong();
        int version = byteBuffer.getInt();
        key = new ContractUint256();
        key.deserialize(byteBuffer, version);
    }

    @Override
    public boolean equals(ByteBuffer byteBuffer, int version) throws IOException {
        if (contractId != byteBuffer.getLong()) return false;
        if (key.getVersion() != byteBuffer.getInt()) return false;
        return key.equals(byteBuffer, version);
    }
}
