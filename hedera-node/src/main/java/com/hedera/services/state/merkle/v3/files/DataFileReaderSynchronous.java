package com.hedera.services.state.merkle.v3.files;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * A data file reader implementation based on using NIO2 SeekableByteChannel. To allow multithreaded access it uses
 * simple method synchronization.
 */
public final class DataFileReaderSynchronous extends DataFileReader {
    public final ByteBuffer intSizeByteBuffer;
    private final SeekableByteChannel seekableByteChannel;

    /**
     * Open an existing data file, reading the metadata from the file
     *
     * @param path the path to the data file
     */
    public DataFileReaderSynchronous(Path path) throws IOException {
        super(path);
        this.seekableByteChannel =  Files.newByteChannel(path, StandardOpenOption.READ);
        intSizeByteBuffer = hasVariableSizedData ? ByteBuffer.allocateDirect(Integer.BYTES) : null;
    }

    /**
     * Open an existing data file, using the provided metadata
     *
     * @param path the path to the data file
     * @param metadata the file's metadata to save loading from file
     */
    public DataFileReaderSynchronous(Path path, DataFileMetadata metadata) throws IOException {
        super(path, metadata);
        this.seekableByteChannel =  Files.newByteChannel(path, StandardOpenOption.READ);
        intSizeByteBuffer = hasVariableSizedData ? ByteBuffer.allocateDirect(Integer.BYTES) : null;
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
    protected synchronized int readInt(long byteOffsetInFile) throws IOException {
        intSizeByteBuffer.clear();
        seekableByteChannel.position(byteOffsetInFile);
        seekableByteChannel.read(intSizeByteBuffer);
        intSizeByteBuffer.rewind();
        return intSizeByteBuffer.getInt();
    }

    /**
     * Read data from the file starting at byteOffsetInFile till we fill the bufferToFill.remaining() bytes.
     *
     * @param byteOffsetInFile offset to start reading at
     * @param bufferToFill     byte buffer to fill with read data
     * @throws IOException if there was a problem reading
     */
    @Override
    protected synchronized void read(long byteOffsetInFile, ByteBuffer bufferToFill) throws IOException {
        seekableByteChannel.position(byteOffsetInFile);
        seekableByteChannel.read(bufferToFill);
    }

    /**
     * Close this data file, it can not be used once closed.
     */
    @Override
    public void close() throws IOException {
        seekableByteChannel.close();
    }
}
