package com.hedera.services.store.contracts.repository;

public interface MemSizeEstimator<E> {
    MemSizeEstimator<byte[]> ByteArrayEstimator = (bytes) -> {
        return bytes == null ? 0L : (long)(bytes.length + 16);
    };

    long estimateSize(E var1);
}
