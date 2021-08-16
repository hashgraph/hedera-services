package com.hedera.services.state.merkle.virtual;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualValue;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ContractValue implements VirtualValue {
    public static final int SERIALIZED_SIZE = ContractUint256.SERIALIZED_SIZE;
    private ContractUint256 value;
    private boolean readOnly = false;

    public ContractValue() {}

    public ContractValue(ContractUint256 value) {
        this.value = value;
    }

    private ContractValue(ContractValue source, boolean readOnly) {
        this.value = source.value;
        this.readOnly = readOnly;
    }

    public ContractUint256 getValue() {
        return value;
    }

    public void setValue(ContractUint256 value) {
        if (!readOnly) {
            this.value = value;
        }
    }

    @Override
    public long getClassId() {
        return 0x9236cf6b7fb53bfeL;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public void deserialize(SerializableDataInputStream serializableDataInputStream, int i) throws IOException {
        this.value = new ContractUint256();
        deserialize(serializableDataInputStream, i);
    }

    @Override
    public void serialize(SerializableDataOutputStream serializableDataOutputStream) throws IOException {
        if (this.value != null) {
            this.value.serialize(serializableDataOutputStream);
        }
    }

    @Override
    public void serialize(ByteBuffer byteBuffer) throws IOException {
        if (this.value != null) {
            this.value.serialize(byteBuffer);
        }
    }

    @Override
    public void deserialize(ByteBuffer byteBuffer, int i) throws IOException {
        this.value = new ContractUint256();
        deserialize(byteBuffer, i);
    }

    @Override
    public ContractValue copy() {
        return new ContractValue(this, false);
    }

    @Override
    public VirtualValue asReadOnly() {
        return new ContractValue(this, true);
    }

    @Override
    public void release() {

    }
}
