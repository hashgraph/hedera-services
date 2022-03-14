package com.hedera.services.state.virtual;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.jasperdb.SelfSerializableSupplier;

import java.io.IOException;

public class UniqueTokenKeySupplier implements SelfSerializableSupplier<UniqueTokenKey> {
    static final long CLASS_ID = 0x8232d5e6ed77cc5cL;
    static final int CURRENT_VERSION = 1;

    @Override
    public void deserialize(SerializableDataInputStream serializableDataInputStream, int i) throws IOException {}

    @Override
    public void serialize(SerializableDataOutputStream serializableDataOutputStream) throws IOException {}

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
