package com.hedera.services.state.merkle.virtual;

import com.hedera.services.store.models.Id;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.tuweni.units.bigints.UInt256;

import java.io.IOException;

/**
 * The key of a key/value pair used by a Smart Contract for storage purposes.
 */
final class ContractKey implements SelfSerializable {
    private final Id contractId;
    private final ContractUint256 key;

    ContractKey(Id contractId, ContractUint256 key) {
        this.contractId = contractId;
        this.key = key;
    }

    @Override
    public long getClassId() {
        return 0xb2c0a1f733950abdL;
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
