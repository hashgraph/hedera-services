package com.hedera.services.state.jasperdb.files;

import java.nio.ByteBuffer;

@FunctionalInterface
public interface KeySizeReader {
    /**
     * Read the size of the key from byte buffer at current position. Assumes current position is at beginning of
     * serialized key. The buffer's position should be restored afterwards, so that key could be deserialized from
     * the byte buffer if needed.
     *
     * @param buffer The buffer to read key's size from
     * @return the key size in bytes
     */
    int getKeySize(ByteBuffer buffer);
}
