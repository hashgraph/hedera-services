package com.hedera.node.app.service.mono.state.codec;

import com.hedera.pbj.runtime.io.DataInput;
import com.swirlds.common.merkle.MerkleLeaf;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Temporary adapter to let us re-use {@link MerkleLeaf}, {@link com.swirlds.virtualmap.VirtualKey},
 * and {@link com.swirlds.virtualmap.VirtualValue} serialization and deserialization logic for
 * {@link com.hedera.pbj.runtime.Codec} implementations.
 */
public class PbjInputStream extends InputStream {
    private final DataInput in;

    private PbjInputStream(@NonNull final DataInput in) {
        this.in = Objects.requireNonNull(in);
    }

    public static @NonNull PbjInputStream wrapping(@NonNull final DataInput in) {
        return new PbjInputStream(Objects.requireNonNull(in));
    }
    @Override
    public int read() throws IOException {
        return in.readByte();
    }
}
