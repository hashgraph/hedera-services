package com.hedera.node.app.blocks.impl;

import java.io.BufferedOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import edu.umd.cs.findbugs.annotations.NonNull;

public class DirectFromParentBufferedOutputStream3 extends BufferedOutputStream {

   public DirectFromParentBufferedOutputStream3(@NonNull final OutputStream outputStream, @NonNull final byte[] buf) {
        super(outputStream, 1);
        this.buf = buf;
    }
}
