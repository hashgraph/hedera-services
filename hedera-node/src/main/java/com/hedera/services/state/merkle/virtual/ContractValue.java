package com.hedera.services.state.merkle.virtual;

import com.hedera.services.store.models.Id;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.IOException;

class ContractValue implements SelfSerializable {
    private final Id contractId;
    private final ContractUint256 key;

    ContractValue(Id contractId, ContractUint256 key) {
        this.contractId = contractId;
        this.key = key;
    }

    @Override
    public long getClassId() {
        return 0x9236cf6b7fb53bfeL;
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
