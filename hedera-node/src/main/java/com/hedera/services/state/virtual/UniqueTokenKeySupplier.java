package com.hedera.services.state.virtual;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.jasperdb.SelfSerializableSupplier;

public class UniqueTokenKeySupplier implements SelfSerializableSupplier<UniqueTokenKey> {
    static final long CLASS_ID = 0x8232d5e6ed77cc5cL;
    static final int CURRENT_VERSION = 1;

    @Override
    public void deserialize(SerializableDataInputStream serializableDataInputStream, int i) {
        /* No operations since no state needs to be restored. */
    }

    @Override
    public void serialize(SerializableDataOutputStream serializableDataOutputStream) {
        /* No operations since no state needs to be saved. */
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
    public UniqueTokenKey get() {
        return new UniqueTokenKey();
    }
}
