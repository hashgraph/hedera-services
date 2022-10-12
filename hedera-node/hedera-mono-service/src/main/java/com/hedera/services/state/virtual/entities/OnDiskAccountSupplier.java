package com.hedera.services.state.virtual.entities;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.SelfSerializableSupplier;
import java.io.IOException;

public class OnDiskAccountSupplier
        implements SelfSerializableSupplier<OnDiskAccount> {
    static final long CLASS_ID = 0xe5d01987257f5efcL;
    static final int CURRENT_VERSION = 1;

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version)
            throws IOException {
        // Nothing to do here
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
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
    public OnDiskAccount get() {
        return new OnDiskAccount();
    }
}
