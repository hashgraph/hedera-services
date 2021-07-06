package com.hedera.services.state.merkle.virtual;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.fcmap.VKey;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Objects;

public class ContractUint256BigInteger implements SelfSerializable, VKey {
    public static final int SERIALIZED_SIZE = Integer.BYTES + 32; // 32 for BigInt of 256 bytes
    private BigInteger value;

    public ContractUint256BigInteger() {
        // Should only be used by deserialization...
    }

    public ContractUint256BigInteger(BigInteger value) {
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
        if (length == 0) {
            throw new IOException("This should not happen, read BigInteger array length is 0");
        } else {
            final var bytes = serializableDataInputStream.readNBytes(length);
            this.value = new BigInteger(bytes);
        }
    }

    @Override
    public void serialize(SerializableDataOutputStream serializableDataOutputStream) throws IOException {
        final var arr = this.value.toByteArray();
        if (arr.length == 0) System.err.println("This should not happen, BigInteger["+this.value+"] array length is 0");
        serializableDataOutputStream.writeInt(arr.length);
        serializableDataOutputStream.write(arr);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContractUint256BigInteger that = (ContractUint256BigInteger) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public void serialize(ByteBuffer byteBuffer) throws IOException {
        final var arr = this.value.toByteArray();
        if (arr.length > 32) throw new RuntimeException("ContractUint256.serialize got more than 32 bytes from BigInteger ["+this.value+"]");
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
