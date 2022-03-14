package com.hedera.services.state.virtual;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.jasperdb.SelfSerializableSupplier;

import java.io.IOException;

public class UniqueTokenValueSupplier implements SelfSerializableSupplier<UniqueTokenValue> {
    static final long CLASS_ID = 0xc4d512c6695451d4L;
    static final int CURRENT_VERSION = 1;

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {}

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {}

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public UniqueTokenValue get() {
        return new UniqueTokenValue();
    }
}
