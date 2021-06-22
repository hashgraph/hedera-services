package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import java.nio.ByteBuffer;

/**
 * Interface for a class that can be read and written to a ByteBuffer
 */
public interface ByteBufferSerializable {
    /**
     * Write this classes data to the given ByteBuffer
     *
     * @param buffer the ByteBuffer to write to
     */
    public void write(ByteBuffer buffer);

    /**
     * Read this classes data from the given ByteBuffer
     *
     * @param buffer the ByteBuffer to read from
     */
    public void read(ByteBuffer buffer);
}
