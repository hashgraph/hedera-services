package com.hedera.services.state.merkle.virtual;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.fcmap.VKey;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Representation of a 256bit unsigned int, stored internally as 4 longs.
 */
public class ContractUint256 implements SelfSerializable, VKey {
    public static final int SERIALIZED_SIZE = Long.BYTES * 4; // 32 for BigInt of 256 bytes
    private long value_0 = 0; // low order
    private long value_1 = 0;
    private long value_2 = 0;
    private long value_3 = 0; // high order

    public ContractUint256() {
        // Should only be used by deserialization...
    }

    public ContractUint256(BigInteger value) {
        Objects.requireNonNull(value);
        value_0 = value.longValue(); // TODO, only works for BigIntegers less than a long
    }

    public ContractUint256(long value) {
        this.value_0 = value;
    }

    @Override
    public long getClassId() {
        return 0xd7c4802f00979857L;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public void deserialize(SerializableDataInputStream inputStream, int i) throws IOException {
        value_3 = inputStream.readLong();
        value_2 = inputStream.readLong();
        value_1 = inputStream.readLong();
        value_0 = inputStream.readLong();
    }

    @Override
    public void serialize(SerializableDataOutputStream outputStream) throws IOException {
        outputStream.writeLong(value_3);
        outputStream.writeLong(value_2);
        outputStream.writeLong(value_1);
        outputStream.writeLong(value_0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContractUint256 uint256 = (ContractUint256) o;
        return value_0 == uint256.value_0 && value_1 == uint256.value_1 && value_2 == uint256.value_2 && value_3 == uint256.value_3;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value_0, value_1, value_2, value_3);
    }

    @Override
    public void serialize(ByteBuffer byteBuffer) throws IOException {
        byteBuffer.putLong(value_3);
        byteBuffer.putLong(value_2);
        byteBuffer.putLong(value_1);
        byteBuffer.putLong(value_0);
    }

    @Override
    public void deserialize(ByteBuffer byteBuffer, int i) throws IOException {
        value_3 = byteBuffer.getLong();
        value_2 = byteBuffer.getLong();
        value_1 = byteBuffer.getLong();
        value_0 = byteBuffer.getLong();
    }

    @Override
    public boolean equals(ByteBuffer byteBuffer, int i) throws IOException {
        return value_3 == byteBuffer.getLong() &&
               value_2 == byteBuffer.getLong() &&
               value_1 == byteBuffer.getLong() &&
               value_0 == byteBuffer.getLong();
    }

    @Override
    public String toString() {
        return "ContractUint256{" +
                "value_0=" + value_0 +
                ", value_1=" + value_1 +
                ", value_2=" + value_2 +
                ", value_3=" + value_3 +
                '}';
    }
}
