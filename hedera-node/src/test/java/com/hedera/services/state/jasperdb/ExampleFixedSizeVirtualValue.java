package com.hedera.services.state.jasperdb;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualValue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

@SuppressWarnings("ResultOfMethodCallIgnored")
public final class ExampleFixedSizeVirtualValue implements VirtualValue {
    public static final int RANDOM_BYTES = 32;
    public static final int SIZE_BYTES = Integer.BYTES + RANDOM_BYTES;
    static final byte[] RANDOM_DATA = new byte[RANDOM_BYTES];
    static {
        new Random(12234).nextBytes(RANDOM_DATA);
    }

    private int id;
    private byte[] data;

    public ExampleFixedSizeVirtualValue() {}

    public ExampleFixedSizeVirtualValue(int id) {
        this.id = id;
        this.data = RANDOM_DATA;
    }

    @Override
    public void deserialize(SerializableDataInputStream inputStream, int i) throws IOException {
        id = inputStream.readInt();
        data = new byte[RANDOM_BYTES];
        inputStream.read(data);
    }

    @Override
    public void serialize(SerializableDataOutputStream outputStream) throws IOException {
        outputStream.writeInt(id);
        outputStream.write(data);
    }

    @Override
    public void serialize(ByteBuffer buffer) throws IOException {
        buffer.putInt(id);
        buffer.put(data);
    }

    @Override
    public void deserialize(ByteBuffer buffer, int version) throws IOException {
        id = buffer.getInt();
        data = new byte[RANDOM_BYTES];
        buffer.get(data);
    }

    @Override
    public VirtualValue copy() {
        return this;
    }

    @Override
    public VirtualValue asReadOnly() {
        return this;
    }

    @Override
    public long getClassId() {
        return 1234;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExampleFixedSizeVirtualValue that = (ExampleFixedSizeVirtualValue) o;
        return id == that.id && Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id);
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }

    @Override
    public String toString() {
        return "TestLeafData{" +
                "id=" + id +
                ", data=" + Arrays.toString(data) +
                '}';
    }

    @Override
    public void release() {}
}
