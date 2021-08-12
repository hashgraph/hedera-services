package com.hedera.services.state.merkle.v3.files;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutionException;

/**
 * A data file reader implementation based on using NIO2 AsynchronousFileChannel this allows multiple concurrent readers.
 */
public final class DataFileReaderAsynchronous extends DataFileReader {
    private final AsynchronousFileChannel asynchronousFileChannel;
    private final ThreadLocal<ByteBuffer> intSizeByteBuffer = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(Integer.BYTES));

    /**
     * Open an existing data file, reading the metadata from the file
     *
     * @param path the path to the data file
     */
    public DataFileReaderAsynchronous(Path path) throws IOException {
        super(path);
        // open reading channel
        asynchronousFileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.READ);
    }

    /**
     * Open an existing data file, using the provided metadata
     *
     * @param path the path to the data file
     * @param metadata the file's metadata to save loading from file
     */
    public DataFileReaderAsynchronous(Path path, DataFileMetadata metadata) throws IOException {
        super(path, metadata);
        // open reading channel
        asynchronousFileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.READ);
    }

    /**
     * Read an integer from the file starting at byteOffsetInFile.
     *
     * NOTE:This is only called if hasVariableSizedData is true
     *
     * @param byteOffsetInFile offset to start reading at
     * @return the read integer
     * @throws IOException if there was a problem reading
     */
    @Override
    protected int readInt(long byteOffsetInFile) throws IOException {
        try {
            ByteBuffer intSizeByteBuffer = this.intSizeByteBuffer.get();
            intSizeByteBuffer.clear();
            asynchronousFileChannel.read(intSizeByteBuffer, byteOffsetInFile).get();
            intSizeByteBuffer.rewind();
            return intSizeByteBuffer.getInt();
        } catch (InterruptedException | ExecutionException e) {
            throw new IOException(e);
        }
    }

    /**
     * Read data from the file starting at byteOffsetInFile till we fill the bufferToFill.remaining() bytes.
     *
     * @param byteOffsetInFile offset to start reading at
     * @param bufferToFill     byte buffer to fill with read data
     * @throws IOException if there was a problem reading
     */
    @Override
    protected void read(long byteOffsetInFile, ByteBuffer bufferToFill) throws IOException {
        try {
            asynchronousFileChannel.read(bufferToFill,byteOffsetInFile).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IOException(e);
        }
    }

    /**
     * Close this data file, it can not be used once closed.
     */
    @Override
    public void close() throws IOException {
        asynchronousFileChannel.close();
    }
}
