package com.hedera.node.app.blocks.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import edu.umd.cs.findbugs.annotations.NonNull;

public class BypassParentGZIPOutputStream2 extends GZIPOutputStream {

    public BypassParentGZIPOutputStream2(OutputStream out, byte[] buf)
            throws IOException {
        super(out, 0);
        this.buf = buf;
    }

    @Override
    public void write(@NonNull byte[] b, int off, int len)
            throws IOException {
        // Bypass the GZIPOutputStream implementation to avoid the unneeded synchronization
        ((DeflaterOutputStream) this).write(b, off, len);
        crc.update(buf, off, len);
    }
}

