package com.hedera.node.app.service.network.impl.serdes;


import com.hedera.node.app.service.mono.state.merkle.MerkleSpecialFiles;
import com.hedera.node.app.spi.state.Serdes;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class MonoSpecialFilesAdapterSerdes implements Serdes<MerkleSpecialFiles> {
    @NonNull
    @Override
    public MerkleSpecialFiles parse(final @NonNull DataInput input) throws IOException {
        if (input instanceof SerializableDataInputStream in) {
            final var context = new MerkleSpecialFiles();
            context.deserialize(in, MerkleSpecialFiles.CURRENT_VERSION);
            return context;
        } else {
            throw new IllegalArgumentException("Expected a SerializableDataInputStream");
        }
    }

    @Override
    public void write(
            final @NonNull MerkleSpecialFiles item,
            final @NonNull DataOutput output) throws IOException {
        if (output instanceof SerializableDataOutputStream out) {
            item.serialize(out);
        } else {
            throw new IllegalArgumentException("Expected a SerializableDataOutputStream");
        }
    }

    @Override
    public int measure(@NonNull DataInput input) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int typicalSize() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean fastEquals(@NonNull MerkleSpecialFiles item, @NonNull DataInput input) {
        throw new UnsupportedOperationException();
    }
}
