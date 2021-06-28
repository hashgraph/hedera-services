package com.hedera.services.state.merkle.virtual;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Objects;

public class ContractUint256 implements SelfSerializable {
    public static final int SERIALIZED_SIZE = 32;
    private BigInteger value;

    public ContractUint256() {
        // Should only be used by deserialization...
    }

    public ContractUint256(BigInteger value) {
        this.value = Objects.requireNonNull(value);
    }

    @Override
    public long getClassId() {
        return 0xd7c4802f0b91f057L;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public void deserialize(SerializableDataInputStream serializableDataInputStream, int i) throws IOException {
        final var bytes = serializableDataInputStream.readByteArray(32);
        this.value = new BigInteger(bytes);
    }

    @Override
    public void serialize(SerializableDataOutputStream serializableDataOutputStream) throws IOException {
        final var arr = this.value.toByteArray();
        if (arr.length > 32) {
            System.out.println(arr.length);
        }
        serializableDataOutputStream.writeByteArray(arr);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContractUint256 that = (ContractUint256) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
