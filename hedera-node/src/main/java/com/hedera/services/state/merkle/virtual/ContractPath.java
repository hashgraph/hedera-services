package com.hedera.services.state.merkle.virtual;

import com.hedera.services.store.models.Id;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.IOException;

class ContractPath implements SelfSerializable {
    private final Id contractId;
    private final long path;

    ContractPath(Id contractId, long path) {
        this.contractId = contractId;
        this.path = path;
    }

    @Override
    public long getClassId() {
        return 0xf1d40d8877cf80eL;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public void deserialize(SerializableDataInputStream serializableDataInputStream, int i) throws IOException {

    }

    @Override
    public void serialize(SerializableDataOutputStream serializableDataOutputStream) throws IOException {

    }
}
