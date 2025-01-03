package com.hedera.node.app.blocks.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

import edu.umd.cs.findbugs.annotations.NonNull;

public class DirectFromParentGZIPOutputStream3 extends GZIPOutputStream {

    public DirectFromParentGZIPOutputStream3(@NonNull final OutputStream out, @NonNull final byte[] buf)
            throws IOException {
        super(out, 1);
        this.buf = buf;
    }
}

