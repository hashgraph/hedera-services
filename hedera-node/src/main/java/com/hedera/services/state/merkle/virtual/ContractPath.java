package com.hedera.services.state.merkle.virtual;

import com.hedera.services.store.models.Id;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.IOException;
import java.util.Objects;

public class ContractPath implements SelfSerializable {
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
