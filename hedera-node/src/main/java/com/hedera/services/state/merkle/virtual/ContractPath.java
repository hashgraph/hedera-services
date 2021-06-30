package com.hedera.services.state.merkle.virtual;

import com.hedera.services.store.models.Id;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.fcmap.VKey;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class ContractPath implements SelfSerializable, VKey {
    public static final int SERIALIZED_SIZE = Long.BYTES * 4;
    private Id contractId;
    private long path;

    public ContractPath() {
        // there has to be a default constructor for deserialize
    }

    ContractPath(Id contractId, long path) {
        this.contractId = contractId;
        this.path = path;
    }

    public Id getContractId() {
        return contractId;
    }

    public long getPath() {
        return path;
    }

    @Override
    public long getClassId() {
        return 0xf1d40d8877cf80eL;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int i) throws IOException {
        contractId = new Id(in.readLong(),in.readLong(),in.readLong());
        path = in.readLong();
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeLong(contractId.getShard());
        out.writeLong(contractId.getRealm());
        out.writeLong(contractId.getNum());
        out.writeLong(path);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContractPath that = (ContractPath) o;
        return path == that.path && Objects.equals(contractId, that.contractId);
    }

    @Override
    public void serialize(ByteBuffer byteBuffer) throws IOException {
        byteBuffer.putLong(contractId.getShard());
        byteBuffer.putLong(contractId.getRealm());
        byteBuffer.putLong(contractId.getNum());
        byteBuffer.putLong(path);
    }

    @Override
    public void deserialize(ByteBuffer byteBuffer, int i) throws IOException {
        contractId = new Id(byteBuffer.getLong(), byteBuffer.getLong(), byteBuffer.getLong());
        path = byteBuffer.getLong();
    }

    @Override
    public boolean equals(ByteBuffer byteBuffer, int i) throws IOException {
        if (contractId.getShard() != byteBuffer.getLong()) return false;
        if (contractId.getRealm() != byteBuffer.getLong()) return false;
        if (contractId.getNum() != byteBuffer.getLong()) return false;
        return path == byteBuffer.getLong();
    }

    @Override
    public int hashCode() {
        return Objects.hash(contractId, path);
    }

    @Override
    public String toString() {
        return "ContractPath{" +
                "path=" + path +
                '}';
    }
}
