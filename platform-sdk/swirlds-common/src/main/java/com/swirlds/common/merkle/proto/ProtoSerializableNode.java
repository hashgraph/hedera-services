package com.swirlds.common.merkle.proto;

import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.common.io.exceptions.MerkleSerializationException;
import java.nio.file.Path;

public interface ProtoSerializableNode extends ProtoSerializableBase {

    default void protoSerialize(final WritableSequentialData out, final Path artifactsDir)
            throws MerkleSerializationException {
        throw new UnsupportedOperationException("TO IMPLEMENT " + getClass().getName() + ".protoSerialize()");
    }
}
