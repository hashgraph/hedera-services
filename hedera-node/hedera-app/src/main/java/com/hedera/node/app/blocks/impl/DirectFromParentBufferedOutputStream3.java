package com.hedera.node.app.blocks.impl;

import java.io.BufferedOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An OutputStream that uses {@link ByteBuffer} before writing to an underlying {@link OutputStream}
 */
public class DirectFromParentBufferedOutputStream3 extends BufferedOutputStream {
    /**
     * Creates a Writer that uses an internal {@link ByteBuffer} to buffer writes to the given {@code outputStream}.
     *
     * @param outputStream   the underlying {@link OutputStream} to write to
     * @param buf the underlying array to use as the temporary buffer
     * @throws IllegalArgumentException in case {@code bufferCapacity} is less or equals to 0
     * @throws NullPointerException     in case {@code outputStream} is null
     */
    public DirectFromParentBufferedOutputStream3(@NonNull final OutputStream outputStream, @NonNull final byte[] buf) {
        super(outputStream, 0);
        this.buf = buf;
    }
}
