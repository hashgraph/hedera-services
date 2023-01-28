package com.hedera.node.app.service.network.impl.serdes;

import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.state.Serdes;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class EntityNumSerdes implements Serdes<EntityNum> {
    @NonNull
    @Override
    public EntityNum parse(final @NonNull DataInput input) throws IOException {
        if (input instanceof SerializableDataInputStream in) {
            return new EntityNum(in.readInt());
        } else {
            throw new IllegalArgumentException("Expected a SerializableDataInputStream");
        }
    }

    @Override
    public void write(final @NonNull EntityNum item, final @NonNull DataOutput output) throws IOException {
        if (output instanceof SerializableDataOutputStream out) {
            out.writeInt(item.intValue());
        } else {
            throw new IllegalArgumentException("Expected a SerializableDataOutputStream");
        }
    }

    @Override
    public int measure(final @NonNull DataInput input) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int typicalSize() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean fastEquals(final @NonNull EntityNum item, final @NonNull DataInput input) {
        throw new UnsupportedOperationException();
    }
}
