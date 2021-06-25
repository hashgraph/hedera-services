package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

public class PositionableByteBufferSerializableDataInputStream extends SerializableDataInputStream {
    private final ByteBuffer buffer;

    public PositionableByteBufferSerializableDataInputStream(ByteBuffer buffer) {
        super(new ByteBufferInputStream(buffer));
        this.buffer = buffer;
    }

    public int position() {
        return buffer.position();
    }

    public void position(int position) {
        buffer.position(position);
    }

    public <T extends SelfSerializable> T readSelfSerializable(int startOffset, Supplier<T> constructor) throws IOException {
        buffer.position(startOffset);
        return readSelfSerializable(constructor);
    }

    public <T extends SelfSerializable> T readSelfSerializable(Supplier<T> constructor) throws IOException {
        SerializableDataInputStream inputStream = new SerializableDataInputStream(new ByteBufferInputStream(buffer));
        int version = inputStream.readInt();
        T object = constructor.get();
        object.deserialize(inputStream, version);
        return object;
    }
}
