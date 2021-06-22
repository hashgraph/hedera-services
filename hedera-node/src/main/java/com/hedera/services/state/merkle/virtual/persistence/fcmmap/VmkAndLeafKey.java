package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Class for a combined VirtualMap key and leaf key
 *
 * @param <VMK> the type for the VirtualMap key
 * @param <LK>  the type for the leaf key
 */
public class VmkAndLeafKey<VMK extends ByteBufferSerializable, LK extends ByteBufferSerializable> implements ByteBufferSerializable {
    public final VMK virtualMapKey;
    public final LK leafKey;

    public VmkAndLeafKey(VMK virtualMapKey, LK leafKey) {
        this.virtualMapKey = virtualMapKey;
        this.leafKey = leafKey;
    }


    /**
     * Write this key to a bytebuffer for storage
     *
     * @param byteBuffer to write to
     */
    @Override
    public void write(ByteBuffer byteBuffer) {
        virtualMapKey.write(byteBuffer);
        leafKey.write(byteBuffer);
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
        @SuppressWarnings("unchecked") VmkAndLeafKey<VMK, LK> that = (VmkAndLeafKey<VMK, LK>) o;
        return Objects.equals(virtualMapKey, that.virtualMapKey) && Objects.equals(leafKey, that.leafKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(virtualMapKey, leafKey);
    }
}
