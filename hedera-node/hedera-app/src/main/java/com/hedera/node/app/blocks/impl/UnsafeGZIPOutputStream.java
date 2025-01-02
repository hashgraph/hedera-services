package com.hedera.node.app.blocks.impl;

import java.io.FilterOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This class implements a stream filter for writing compressed data in
 * the GZIP file format.
 * @author      David Connelly
 * @since 1.1
 *
 */
public class UnsafeGZIPOutputStream extends FilterOutputStream {
    /**
     * CRC-32 of uncompressed data.
     */
    protected CRC32 crc = new CRC32();

    protected Deflater def;
    boolean usesDefaultDeflater;
    protected byte[] buf;
    private final boolean syncFlush;

    /*
     * GZIP header magic number.
     */
    private static final int GZIP_MAGIC = 0x8b1f;

    /*
     * Trailer size in bytes.
     *
     */
    private static final int TRAILER_SIZE = 8;

    // Represents the default "unknown" value for OS header, per RFC-1952
    private static final byte OS_UNKNOWN = (byte) 255;

    /**
     * Creates a new output stream with the specified buffer size and
     * flush mode.
     *
     * @param out the output stream
     * @param buf the output buffer
     * @param syncFlush
     *        if {@code true} invocation of the inherited
     *        {@link DeflaterOutputStream#flush() flush()} method of
     *        this instance flushes the compressor with flush mode
     *        {@link Deflater#SYNC_FLUSH} before flushing the output
     *        stream, otherwise only flushes the output stream
     * @throws    IOException If an I/O error has occurred.
     * @throws    IllegalArgumentException if {@code size <= 0}
     *
     * @since 1.7
     */
    public UnsafeGZIPOutputStream(OutputStream out, byte[] buf, boolean syncFlush)
            throws IOException
    {
        this(out, new Deflater(Deflater.DEFAULT_COMPRESSION, true), true, buf, syncFlush);
   }

    public UnsafeGZIPOutputStream(OutputStream out,
            Deflater def,
            boolean usesDefaultDeflater,
            byte[] buf,
            boolean syncFlush) throws IOException {
        super(out);
        if (out == null || def == null || buf == null) {
            throw new NullPointerException();
        }

        this.def = def;
        this.buf = buf;
        this.syncFlush = syncFlush;
        this.usesDefaultDeflater = usesDefaultDeflater;

        writeHeader();
        crc.reset();
    }

    /**
     * Writes array of bytes to the compressed output stream. This method
     * will block until all the bytes are written.
     * @param b the data to be written
     * @param off the start offset of the data
     * @param len the length of the data
     * @throws    IOException If an I/O error has occurred.
     */
    public void write(@NonNull byte[] b, int off, int len)
            throws IOException
    {
        if (def.finished()) {
            throw new IOException("write beyond end of stream");
        }
        if ((off | len | (off + len) | (b.length - (off + len))) < 0) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }
        if (!def.finished()) {
            def.setInput(b, off, len);
            while (!def.needsInput()) {
                deflate();
            }
        }


        crc.update(b, off, len);
    }

    protected void deflate() throws IOException {
        int len = def.deflate(buf, 0, buf.length);
        if (len > 0) {
            out.write(buf, 0, len);
        }
    }

    /**
     * Finishes writing compressed data to the output stream without closing
     * the underlying stream. Use this method when applying multiple filters
     * in succession to the same output stream.
     * @throws    IOException if an I/O error has occurred
     */
    public void finish() throws IOException {
        if (!def.finished()) {
            try {
                def.finish();
                while (!def.finished()) {
                    int len = def.deflate(buf, 0, buf.length);
                    if (def.finished() && len <= buf.length - TRAILER_SIZE) {
                        // last deflater buffer. Fit trailer at the end
                        writeTrailer(buf, len);
                        len = len + TRAILER_SIZE;
                        out.write(buf, 0, len);
                        return;
                    }
                    if (len > 0)
                        out.write(buf, 0, len);
                }
                // if we can't fit the trailer at the end of the last
                // deflater buffer, we write it separately
                byte[] trailer = new byte[TRAILER_SIZE];
                writeTrailer(trailer, 0);
                out.write(trailer);
            } catch (IOException e) {
                if (usesDefaultDeflater) {
                    def.end();
                }

                throw e;
            }
        }
    }

    public void flush() throws IOException {
        if (syncFlush && !def.finished()) {
            int len = 0;
            while ((len = def.deflate(buf, 0, buf.length, Deflater.SYNC_FLUSH)) > 0)
            {
                out.write(buf, 0, len);
                if (len < buf.length)
                    break;
            }
        }
        out.flush();
    }

    /*
     * Writes GZIP member header.
     */
    private void writeHeader() throws IOException {
        out.write(new byte[] {
                (byte) GZIP_MAGIC,        // Magic number (short)
                (byte)(GZIP_MAGIC >> 8),  // Magic number (short)
                Deflater.DEFLATED,        // Compression method (CM)
                0,                        // Flags (FLG)
                0,                        // Modification time MTIME (int)
                0,                        // Modification time MTIME (int)
                0,                        // Modification time MTIME (int)
                0,                        // Modification time MTIME (int)
                0,                        // Extra flags (XFLG)
                OS_UNKNOWN                // Operating system (OS)
        });
    }

    /*
     * Writes GZIP member trailer to a byte array, starting at a given
     * offset.
     */
    private void writeTrailer(byte[] buf, int offset) throws IOException {
        writeInt((int)crc.getValue(), buf, offset); // CRC-32 of uncompr. data
        writeInt(def.getTotalIn(), buf, offset + 4); // Number of uncompr. bytes
    }

    /*
     * Writes integer in Intel byte order to a byte array, starting at a
     * given offset.
     */
    private void writeInt(int i, byte[] buf, int offset) throws IOException {
        writeShort(i & 0xffff, buf, offset);
        writeShort((i >> 16) & 0xffff, buf, offset + 2);
    }

    /*
     * Writes short integer in Intel byte order to a byte array, starting
     * at a given offset
     */
    private void writeShort(int s, byte[] buf, int offset) throws IOException {
        buf[offset] = (byte)(s & 0xff);
        buf[offset + 1] = (byte)((s >> 8) & 0xff);
    }
}

