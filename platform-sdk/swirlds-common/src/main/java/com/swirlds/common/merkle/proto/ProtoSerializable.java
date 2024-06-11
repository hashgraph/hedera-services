package com.swirlds.common.merkle.proto;

import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.common.io.exceptions.MerkleSerializationException;

public interface ProtoSerializable extends ProtoSerializableBase {

    default void protoSerialize(final WritableSequentialData out) throws MerkleSerializationException {
        throw new UnsupportedOperationException("TO IMPLEMENT " + getClass().getName() + ".protoSerialize()");
    }
}
