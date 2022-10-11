package com.hedera.services.state.virtual.utils;

import java.io.IOException;

public class EntityIoUtils {
    public static void writeBytes(
            final byte[] data,
            final CheckedConsumer<Integer> writeIntFn,
            final CheckedConsumer<byte[]> writeBytesFn)
            throws IOException {
        writeIntFn.accept(data.length);
        writeBytesFn.accept(data);
    }

    public static byte[] readBytes(
            final CheckedSupplier<Integer> readIntFn,
            final CheckedConsumer<byte[]> readBytesFn)
            throws IOException {
        final var len = readIntFn.get();
        final var data = new byte[len];
        readBytesFn.accept(data);
        return data;
    }
}
