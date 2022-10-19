package com.hedera.services.state.virtual.entities;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.SelfSerializableSupplier;

import java.io.IOException;

public class OnDiskTokenRelSupplier implements SelfSerializableSupplier<OnDiskTokenRel> {
    static final long CLASS_ID = 0x0e52cff909625f55L;
    static final int CURRENT_VERSION = 1;

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        // Nothing to do here
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        // Nothing to do here
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public OnDiskTokenRel get() {
        return new OnDiskTokenRel();
    }
}
