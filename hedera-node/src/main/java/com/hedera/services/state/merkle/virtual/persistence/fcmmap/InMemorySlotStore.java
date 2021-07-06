package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import com.hedera.services.state.merkle.virtual.persistence.PositionableByteBufferSerializableDataInputStream;
import com.hedera.services.state.merkle.virtual.persistence.PositionableByteBufferSerializableDataOutputStream;
import com.hedera.services.state.merkle.virtual.persistence.SlotStore;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Very simple slot store implementation that uses a ConcurrentHashMap for testing.
 */
public class InMemorySlotStore implements SlotStore {

    private final ConcurrentHashMap<Long,byte[]> data = new ConcurrentHashMap<>();
    private final AtomicLong index = new AtomicLong(0);
    private final int dataSize;

    public InMemorySlotStore(int dataSize) {
        this.dataSize = dataSize;
    }

    /**
     * Constructor to match SlotStoreFactory
     *
     * @param slotSizeBytes Slot data size in bytes
     * @param fileSize The size of each storage file in bytes
     * @param storageDirectory The path of the directory to store storage files
     * @param filePrefix The prefix for each storage file
     * @param fileExtension The extension for each storage file, for example "dat"
     */
    public InMemorySlotStore(int slotSizeBytes, int fileSize, Path storageDirectory, String filePrefix, String fileExtension)
            throws IOException {
        this.dataSize = slotSizeBytes;
    }

    @Override
    public int getSize() {
        return data.size();
    }

    @Override
    public void writeSlot(long location, SlotWriter writer) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(dataSize);
        writer.write(new PositionableByteBufferSerializableDataOutputStream(buffer));
//        buffer.rewind();
        data.put(location,buffer.array());
    }

    @Override
    public void updateSlot(long location, SlotWriter writer) throws IOException {
        ByteBuffer buffer;
        byte[] bytes = data.get(location);
        if (bytes != null) { // IS UPDATE
            buffer = ByteBuffer.wrap(Arrays.copyOf(bytes,bytes.length));
        } else { // IS NEW
            buffer = ByteBuffer.allocate(dataSize);
        }
        writer.write(new PositionableByteBufferSerializableDataOutputStream(buffer));
//        buffer.rewind();
        data.put(location,buffer.array());
    }

    @Override
    public <R> R readSlot(long location, SlotReader<R> reader) throws IOException {
        byte[] bytes = data.get(location);
        if (bytes != null) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            try {
                return reader.read(new PositionableByteBufferSerializableDataInputStream(buffer));
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("location = "+location+" buffer.position()"+buffer.position()+" buffer data="+ Arrays.toString(buffer.array()));
                throw e;
            }
        }
        return null;
    }

    @Override
    public void writeSlotByteBuffer(long location, ByteBufferSlotWriter writer) throws IOException {
        ByteBuffer buffer;
        byte[] bytes = data.get(location);
        if (bytes != null) { // IS UPDATE
            buffer = ByteBuffer.wrap(Arrays.copyOf(bytes,bytes.length));
        } else { // IS NEW
            buffer = ByteBuffer.allocate(dataSize);
        }
        writer.write(buffer);
//        buffer.rewind();
        data.put(location,buffer.array());
    }

    @Override
    public <R> R readSlotByteBuffer(long location, ByteBufferSlotReader<R> reader) throws IOException {
        byte[] bytes = data.get(location);
        if (bytes != null) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            try {
                return reader.read(buffer);
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("location = "+location+" buffer.position()"+buffer.position()+" buffer data="+ Arrays.toString(buffer.array()));
                throw e;
            }
        }
        return null;
    }

    @Override
    public long getNewSlot() {
        return index.getAndIncrement();
    }

    @Override
    public void deleteSlot(long location) throws IOException {
        data.remove(location);
    }

    // =================================================================================================================
    // Not needed

    @Override
    public void close() {}

    @Override
    public Object acquireWriteLock(long location) {
        return null;
    }

    @Override
    public void releaseWriteLock(long location, Object lockStamp) {}

    @Override
    public Object acquireReadLock(long location) {
        return null;
    }

    @Override
    public void releaseReadLock(long location, Object lockStamp) {}
}
