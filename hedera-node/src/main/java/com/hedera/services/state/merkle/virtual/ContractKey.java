package com.hedera.services.state.merkle.virtual;

import com.hedera.services.store.models.Id;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.fcmap.VKey;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * The key of a key/value pair used by a Smart Contract for storage purposes.
 */
public final class ContractKey implements VKey {
    public static final int SERIALIZED_SIZE = Long.BYTES + Long.BYTES + Long.BYTES + Integer.BYTES + ContractUint256.SERIALIZED_SIZE;
    private Id contractId;
    private ContractUint256 key;

    public ContractKey() {
        // there has to be a default constructor for deserialize
    }

    public ContractKey(Id contractId, ContractUint256 key) {
        this.contractId = contractId;
        this.key = key;
    }

    public Id getContractId() {
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
    public void deserialize(SerializableDataInputStream in, int i) throws IOException {
        contractId = new Id(in.readLong(),in.readLong(),in.readLong());
        int version = in.readInt();
        key = new ContractUint256();
        key.deserialize(in,version);
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeLong(contractId.getShard());
        out.writeLong(contractId.getRealm());
        out.writeLong(contractId.getNum());
        out.writeInt(key.getVersion());
        key.serialize(out);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContractKey that = (ContractKey) o;
        return Objects.equals(contractId, that.contractId) && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        // was using Objects.hash but it is horrible in hot spot as it creates a Object[] because of var args
        int result = 1;
        result = 31 * result + (contractId == null ? 0 : contractId.hashCode());
        result = 31 * result + (key == null ? 0 : key.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return "ContractKey{" +
                "id={" + contractId.getShard()+","+contractId.getRealm()+","+contractId.getNum()+"}, " +
                key +
                '}';
    }

    @Override
    public void serialize(ByteBuffer byteBuffer) throws IOException {
        byteBuffer.putLong(contractId.getShard());
        byteBuffer.putLong(contractId.getRealm());
        byteBuffer.putLong(contractId.getNum());
        byteBuffer.putInt(key.getVersion());
        key.serialize(byteBuffer);
    }

    @Override
    public void deserialize(ByteBuffer byteBuffer, int i) throws IOException {
        contractId = new Id(byteBuffer.getLong(), byteBuffer.getLong(), byteBuffer.getLong());
        int version = byteBuffer.getInt();
        key = new ContractUint256();
        key.deserialize(byteBuffer, version);
    }

    @Override
    public boolean equals(ByteBuffer byteBuffer, int version) throws IOException {
        if (contractId.getShard() != byteBuffer.getLong()) return false;
        if (contractId.getRealm() != byteBuffer.getLong()) return false;
        if (contractId.getNum() != byteBuffer.getLong()) return false;
        if (key.getVersion() != byteBuffer.getInt()) return false;
        return key.equals(byteBuffer, version);
    }
}
