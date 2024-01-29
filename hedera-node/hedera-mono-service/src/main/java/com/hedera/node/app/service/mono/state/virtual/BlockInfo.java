package com.hedera.node.app.service.mono.state.virtual;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class BlockInfo implements VirtualKey {


    static final long RUNTIME_CONSTRUCTABLE_ID = 0xb2c0a1f73ab38ac8a;
    static final int MERKLE_VERSION = 1;

    @Override
    public long getClassId() {
        return 0;
    }

    @Override
    public void serialize(@NotNull SerializableDataOutputStream out) throws IOException {

    }

    @Override
    public void deserialize(@NotNull SerializableDataInputStream in, int version) throws IOException {

    }

    @Override
    public int getMinimumSupportedVersion() {
        return VirtualKey.super.getMinimumSupportedVersion();
    }

    @Override
    public int getVersion() {
        return MERKLE_VERSION;
    }
}
