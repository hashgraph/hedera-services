package com.swirlds.common.merkle.proto;

public interface ProtoSerializableBase {

    default int getProtoSizeInBytes() {
        throw new UnsupportedOperationException("TO IMPLEMENT " + getClass().getName() + ".getProtoSizeInBytes()");
    }
}
