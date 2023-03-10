package com.hedera.node.app.service.mono.state.codec;

import com.hedera.pbj.runtime.io.DataOutput;
import com.swirlds.common.merkle.MerkleLeaf;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Temporary adapter to let us re-use {@link MerkleLeaf}, {@link com.swirlds.virtualmap.VirtualKey},
 * and {@link com.swirlds.virtualmap.VirtualValue} serialization and deserialization logic for
 * {@link com.hedera.pbj.runtime.Codec} implementations.
 */
public class PbjOutputStream extends OutputStream {
    private final DataOutput out;

    private PbjOutputStream(@NonNull final DataOutput out) {
        this.out = Objects.requireNonNull(out);
    }

    public static @NonNull PbjOutputStream wrapping(@NonNull final DataOutput out) {
        return new PbjOutputStream(Objects.requireNonNull(out));
    }

    @Override
    public void write(final int b) throws IOException {
        out.writeByte((byte) b);
    }
}
