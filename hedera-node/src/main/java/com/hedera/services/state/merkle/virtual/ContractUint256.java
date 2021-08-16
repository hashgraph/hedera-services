package com.hedera.services.state.merkle.virtual;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Representation of a 256bit unsigned int, stored internally as 4 longs.
 */
@SuppressWarnings({"PointlessBitwiseExpression", "PointlessArithmeticExpression"})
public class ContractUint256 implements VirtualKey, VirtualValue {
    public static final int SERIALIZED_SIZE = Long.BYTES * 4; // 32 for BigInt of 256 bytes
    private long value_0 = 0; // low order
    private long value_1 = 0;
    private long value_2 = 0;
    private long value_3 = 0; // high order

    public ContractUint256() {
        // Should only be used by deserialization...
    }

    private ContractUint256(ContractUint256 source) {
        this.value_0 = source.value_0;
        this.value_1 = source.value_1;
        this.value_2 = source.value_2;
        this.value_3 = source.value_3;
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
        // was using Objects.hash but it is horrible in hot spot as has to box longs into Longs and create a Object[]
        int result = 1;
        result = 31 * result + (int)(value_0 ^ (value_0 >>> 32));
        result = 31 * result + (int)(value_1 ^ (value_1 >>> 32));
        result = 31 * result + (int)(value_2 ^ (value_2 >>> 32));
        result = 31 * result + (int)(value_3 ^ (value_3 >>> 32));
        return result;
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

    public void serialize(byte[] data, int offset) throws IOException {
        data[offset+0] = (byte)(value_3 >>> 56);
        data[offset+1] = (byte)(value_3 >>> 48);
        data[offset+2] = (byte)(value_3 >>> 40);
        data[offset+3] = (byte)(value_3 >>> 32);
        data[offset+4] = (byte)(value_3 >>> 24);
        data[offset+5] = (byte)(value_3 >>> 16);
        data[offset+6] = (byte)(value_3 >>>  8);
        data[offset+7] = (byte)(value_3 >>>  0);
        data[offset+8] = (byte)(value_2 >>> 56);
        data[offset+9] = (byte)(value_2 >>> 48);
        data[offset+10] = (byte)(value_2 >>> 40);
        data[offset+11] = (byte)(value_2 >>> 32);
        data[offset+12] = (byte)(value_2 >>> 24);
        data[offset+13] = (byte)(value_2 >>> 16);
        data[offset+14] = (byte)(value_2 >>>  8);
        data[offset+15] = (byte)(value_2 >>>  0);
        data[offset+16] = (byte)(value_1 >>> 56);
        data[offset+17] = (byte)(value_1 >>> 48);
        data[offset+18] = (byte)(value_1 >>> 40);
        data[offset+19] = (byte)(value_1 >>> 32);
        data[offset+20] = (byte)(value_1 >>> 24);
        data[offset+21] = (byte)(value_1 >>> 16);
        data[offset+22] = (byte)(value_1 >>>  8);
        data[offset+23] = (byte)(value_1 >>>  0);
        data[offset+24] = (byte)(value_0 >>> 56);
        data[offset+25] = (byte)(value_0 >>> 48);
        data[offset+26] = (byte)(value_0 >>> 40);
        data[offset+27] = (byte)(value_0 >>> 32);
        data[offset+28] = (byte)(value_0 >>> 24);
        data[offset+29] = (byte)(value_0 >>> 16);
        data[offset+30] = (byte)(value_0 >>>  8);
        data[offset+31] = (byte)(value_0 >>>  0);
    }

    public void deserialize(byte[] data, int offset) throws IOException {
        value_3 =   (((long)data[offset+0] << 56) +
                    ((long)(data[offset+1] & 255) << 48) +
                    ((long)(data[offset+2] & 255) << 40) +
                    ((long)(data[offset+3] & 255) << 32) +
                    ((long)(data[offset+4] & 255) << 24) +
                    ((data[offset+5] & 255) << 16) +
                    ((data[offset+6] & 255) <<  8) +
                    ((data[offset+7] & 255) <<  0));
        value_2 =   (((long)data[offset+8] << 56) +
                ((long)(data[offset+9] & 255) << 48) +
                ((long)(data[offset+10] & 255) << 40) +
                ((long)(data[offset+11] & 255) << 32) +
                ((long)(data[offset+12] & 255) << 24) +
                ((data[offset+13] & 255) << 16) +
                ((data[offset+14] & 255) <<  8) +
                ((data[offset+15] & 255) <<  0));
        value_1 =   (((long)data[offset+16] << 56) +
                ((long)(data[offset+17] & 255) << 48) +
                ((long)(data[offset+18] & 255) << 40) +
                ((long)(data[offset+19] & 255) << 32) +
                ((long)(data[offset+20] & 255) << 24) +
                ((data[offset+21] & 255) << 16) +
                ((data[offset+22] & 255) <<  8) +
                ((data[offset+23] & 255) <<  0));
        value_0 =   (((long)data[offset+24] << 56) +
                ((long)(data[offset+25] & 255) << 48) +
                ((long)(data[offset+26] & 255) << 40) +
                ((long)(data[offset+27] & 255) << 32) +
                ((long)(data[offset+28] & 255) << 24) +
                ((data[offset+29] & 255) << 16) +
                ((data[offset+30] & 255) <<  8) +
                ((data[offset+31] & 255) <<  0));
    }

    @Override
    public ContractUint256 copy() {
        // It is immutable anyway
        return new ContractUint256(this);
    }

    @Override
    public VirtualValue asReadOnly() {
        // It is immutable anyway
        return this;
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
        try {
            byte[] bytes = new byte[SERIALIZED_SIZE];
            serialize(bytes,0);
            return "ContractUint256{"+new BigInteger(bytes)+"}";
        } catch (IOException e) {
            e.printStackTrace();
            return "ContractUint256{" +
                    value_0 +
                    ", " + value_1 +
                    ", " + value_2 +
                    ", " + value_3 +
                    "}";
        }
    }

    @Override
    public void release() {

    }
}
