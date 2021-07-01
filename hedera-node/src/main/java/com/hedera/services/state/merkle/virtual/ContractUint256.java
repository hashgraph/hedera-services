package com.hedera.services.state.merkle.virtual;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.fcmap.VKey;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

public class ContractUint256 implements SelfSerializable, VKey {
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
        final var length = serializableDataInputStream.readInt();
        final var bytes = serializableDataInputStream.readNBytes(length);
        this.value = new BigInteger(bytes);
    }

    @Override
    public void serialize(SerializableDataOutputStream serializableDataOutputStream) throws IOException {
        final var arr = this.value.toByteArray();
        serializableDataOutputStream.writeInt(arr.length);
        serializableDataOutputStream.write(arr);
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

    @Override
    public void serialize(ByteBuffer byteBuffer) throws IOException {
        final var arr = this.value.toByteArray();
        byteBuffer.putInt(arr.length);
        byteBuffer.put(arr);
    }

    @Override
    public void deserialize(ByteBuffer byteBuffer, int i) throws IOException {
        final var length = byteBuffer.getInt();
        final var bytes = new byte[length];
        byteBuffer.get(bytes);
        this.value = new BigInteger(bytes);
    }

    @Override
    public boolean equals(ByteBuffer byteBuffer, int i) throws IOException {
        final var length = byteBuffer.getInt();
        final var bytes = this.value.toByteArray();
        if (length != bytes.length) return false;
        for (var b : bytes) {
            if (b != byteBuffer.get()) return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "ContractUint256{"+ value +'}';
    }
}
