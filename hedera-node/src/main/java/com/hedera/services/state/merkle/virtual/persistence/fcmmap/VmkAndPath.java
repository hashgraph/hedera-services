package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Class for a combined VirtualMap key and path long
 *
 * @param <VMK> the type for the VirtualMap key
 */
public class VmkAndPath<VMK extends ByteBufferSerializable> implements ByteBufferSerializable {
    private VMK virtualMapKey;
    private long path;

    public VmkAndPath() {
    }

    public VmkAndPath(VMK virtualMapKey, long path) {
        this.virtualMapKey = virtualMapKey;
        this.path = path;
    }

    public VMK virtualMapKey() {
        return virtualMapKey;
    }

    public long path() {
        return path;
    }

    /**
     * Write this key to a bytebuffer for storage
     *
     * @param byteBuffer to write to
     */
    @Override
    public void write(ByteBuffer byteBuffer) {
        virtualMapKey.write(byteBuffer);
        byteBuffer.putLong(path);
    }

    /**
     * Read this key from bytebuffer
     *
     * @param buffer the ByteBuffer to read from
     */
    @Override
    public void read(ByteBuffer buffer) {
//            virtualMapKey = new VMK???? AHHHHHHH~!~
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked") VmkAndPath<VMK> that = (VmkAndPath<VMK>) o;
        return path == that.path && Objects.equals(virtualMapKey, that.virtualMapKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(virtualMapKey, path);
    }
}
