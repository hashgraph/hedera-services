package com.hedera.node.app.service.mono.state.codec;

import com.hedera.pbj.runtime.io.DataInput;
import com.swirlds.common.merkle.MerkleLeaf;

import java.io.IOException;
import java.io.InputStream;

/**
 * Temporary adapter to let us re-use {@link MerkleLeaf}, {@link com.swirlds.virtualmap.VirtualKey},
 * and {@link com.swirlds.virtualmap.VirtualValue} serialization and deserialization logic for
 * {@link com.hedera.pbj.runtime.Codec} implementations.
 */
public class PbjInputStream extends InputStream {
    private final DataInput in;

    private PbjInputStream(final DataInput in) {
        this.in = in;
    }

    public static PbjInputStream wrapping(final DataInput in) {
        return new PbjInputStream(in);
    }
    @Override
    public int read() throws IOException {
        return in.readByte();
    }
}
