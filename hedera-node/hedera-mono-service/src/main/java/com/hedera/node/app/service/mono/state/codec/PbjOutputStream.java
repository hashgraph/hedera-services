package com.hedera.node.app.service.mono.state.codec;

import com.hedera.pbj.runtime.io.DataOutput;
import com.swirlds.common.merkle.MerkleLeaf;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Temporary adapter to let us re-use {@link MerkleLeaf}, {@link com.swirlds.virtualmap.VirtualKey},
 * and {@link com.swirlds.virtualmap.VirtualValue} serialization and deserialization logic for
 * {@link com.hedera.pbj.runtime.Codec} implementations.
 */
public class PbjOutputStream extends OutputStream {
    private final DataOutput out;

    private PbjOutputStream(final DataOutput out) {
        this.out = out;
    }

    public static PbjOutputStream wrapping(final DataOutput out) {
        return new PbjOutputStream(out);
    }

    @Override
    public void write(final int b) throws IOException {
        out.writeByte((byte) b);
    }
}
