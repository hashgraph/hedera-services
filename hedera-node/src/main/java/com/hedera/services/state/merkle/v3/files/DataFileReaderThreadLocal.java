package com.hedera.services.state.merkle.v3.files;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A data file reader implementation based on using NIO2 SeekableByteChannels. To allow multithreaded access it uses
 * multiple SeekableByteChannels one per accessing thread.
 */
public final class DataFileReaderThreadLocal extends DataFileReader {
    private final ThreadLocalSeekableByteChannel seekableByteChannel;

    /**
     * Open an existing data file, reading the metadata from the file
     *
     * @param path the path to the data file
     */
    public DataFileReaderThreadLocal(Path path) throws IOException {
        super(path);
        this.seekableByteChannel = new ThreadLocalSeekableByteChannel(path, hasVariableSizedData);
    }

    /**
     * Open an existing data file, using the provided metadata
     *
     * @param path the path to the data file
     * @param metadata the file's metadata to save loading from file
     */
    public DataFileReaderThreadLocal(Path path, DataFileMetadata metadata) throws IOException {
        super(path, metadata);
        this.seekableByteChannel = new ThreadLocalSeekableByteChannel(path, hasVariableSizedData);
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
        final LocalData localData = this.seekableByteChannel.get();
        localData.intSizeByteBuffer.clear();
        localData.channel.position(byteOffsetInFile);
        localData.channel.read(localData.intSizeByteBuffer);
        localData.intSizeByteBuffer.rewind();
        return localData.intSizeByteBuffer.getInt();
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
        SeekableByteChannel seekableByteChannel = this.seekableByteChannel.get().channel;
        seekableByteChannel.position(byteOffsetInFile);
        seekableByteChannel.read(bufferToFill);
    }

    /**
     * Close this data file, it can not be used once closed.
     */
    public void close() throws IOException {
        seekableByteChannel.close();
    }

    // =================================================================================================================
    // ThreadLocal SeekableByteChannel

    /**
     * A special thread local that keeps track of the SeekableByteChannel's it creates so that they can be closed.
     */
    private static class ThreadLocalSeekableByteChannel extends ThreadLocal<LocalData>
            implements AutoCloseable {
        private final Path path;
        private final boolean hasVariableSizedData;
        private final CopyOnWriteArrayList<SeekableByteChannel> allChannels = new CopyOnWriteArrayList<>();

        /**
         * Create a new ThreadLocalSeekableByteChannel
         *
         * @param path the path to the data file so we can open SeekableByteChannel's on it
         */
        private ThreadLocalSeekableByteChannel(Path path, boolean hasVariableSizedData) {
            this.path = path;
            this.hasVariableSizedData = hasVariableSizedData;
        }

        /**
         * Open a new SeekableByteChannel when a new thread comes along
         */
        @Override
        protected LocalData initialValue() {
            try {
                LocalData localData = new LocalData(path, hasVariableSizedData);
                allChannels.add(localData.channel);
                return localData;
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        /**
         * Closes all channels
         *
         * @throws IOException if this resource cannot be closed
         */
        @Override
        public void close() throws IOException {
            for(var channel: allChannels) channel.close();
        }
    }

    // =================================================================================================================
    // LocalData

    /**
     * Simple struct so we can store two items in thread local
     */
    private static class LocalData {
        public SeekableByteChannel channel;
        public ByteBuffer intSizeByteBuffer;

        public LocalData(Path path, boolean hasVariableSizedData) throws IOException {
            this.channel = Files.newByteChannel(path, StandardOpenOption.READ);
            this.intSizeByteBuffer = hasVariableSizedData ? ByteBuffer.allocateDirect(Integer.BYTES) : null;
        }
    }
}
