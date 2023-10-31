package com.swirlds.platform.gossip.sync;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.airlift.compress.hadoop.HadoopOutputStream;
import io.airlift.compress.lz4.Lz4HadoopStreams;
import java.io.IOException;
import java.io.OutputStream;

/**
 * A wrapper around an aircompressor lz4 compressed {@link HadoopOutputStream}. By default, the aircompressor stream
 * implementation does not have the flush semantics we need. This wrapper fixes that.
 */
public class CompressedOutputStream extends OutputStream {

//    private final

    private final HadoopOutputStream out;

    /**
     * Constructor.
     *
     * @param baseStream the base stream to wrap
     * @param bufferSize the buffer size to use
     */
    public CompressedOutputStream(@NonNull final OutputStream baseStream, int bufferSize) {
        out = new Lz4HadoopStreams(bufferSize).createOutputStream(baseStream);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(final int b) throws IOException {
        out.write(b);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(@NonNull final byte[] b) throws IOException {
        out.write(b);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(@NonNull final byte[] b, final int off, final int len) throws IOException {
        out.write(b, off, len);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flush() throws IOException {
        out.finish();
        out.flush();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        out.close();
    }
}
